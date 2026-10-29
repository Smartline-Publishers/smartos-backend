package com.smartline.smartos.config;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Builds the SMTP transport used for transactional email (password resets).
 *
 * <p>The application reads the documented {@code SMTP_*} / {@code EMAIL_PROVIDER}
 * environment variables, with {@code app.mail.*} and legacy {@code SPRING_MAIL_*}
 * aliases for compatibility. Boot's own {@code spring.mail.*} autoconfiguration is
 * deliberately not used: it accepts an empty host, so {@code MAIL_ENABLED=true}
 * with no reachable server produced a sender that could never connect while the
 * forgot-password endpoint still answered "email sent".
 *
 * <p>Nothing here logs a password. The resolved transport is logged once at
 * startup so a misconfiguration is visible in the first lines of the log rather
 * than only when a user asks for a reset.
 */
@Configuration
public class MailConfig {

  private static final Logger log = LoggerFactory.getLogger(MailConfig.class);

  /** Used when neither SMTP_TIMEOUT_MS nor app.mail.timeout-ms is set. */
  static final int DEFAULT_TIMEOUT_MS = 10_000;

  /**
   * Host/port defaults for the providers we support by name. Explicit
   * SMTP_SERVER/SMTP_PORT values always win; the preset only fills the blanks,
   * which is what makes EMAIL_PROVIDER useful instead of silently ignored.
   */
  private static final Map<String, Preset> PRESETS = Map.of(
      "gmail", new Preset("smtp.gmail.com", 587),
      "outlook", new Preset("smtp-mail.outlook.com", 587),
      "office365", new Preset("smtp.office365.com", 587),
      "sendgrid", new Preset("smtp.sendgrid.net", 587),
      "mailgun", new Preset("smtp.mailgun.org", 587),
      "postmark", new Preset("smtp.postmarkapp.com", 587));

  private final Environment env;

  public MailConfig(Environment env) {
    this.env = env;
  }

  /** The transport. An unconfigured host yields an unusable sender that is never called. */
  @Bean
  public JavaMailSender javaMailSender() {
    Settings settings = settings();
    JavaMailSenderImpl sender = new JavaMailSenderImpl();
    sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
    if (settings.host().isBlank()) {
      // PasswordResetMailer.isEnabled() also requires a host, so this transport
      // is never used. Keeping the bean allows optional injection.
      return sender;
    }

    sender.setHost(settings.host());
    sender.setPort(settings.port());
    if (!settings.username().isBlank()) {
      sender.setUsername(settings.username());
      sender.setPassword(settings.password());
    }

    boolean auth = !settings.username().isBlank();
    Properties props = sender.getJavaMailProperties();
    props.put("mail.transport.protocol", "smtp");
    props.put("mail.smtp.auth", Boolean.toString(auth));
    // Implicit TLS (port 465) and STARTTLS (port 587) are mutually exclusive.
    props.put("mail.smtp.ssl.enable", Boolean.toString(settings.implicitTls()));
    props.put("mail.smtp.starttls.enable", Boolean.toString(settings.startTls()));
    // Refuse to send credentials over plaintext when the server does not offer
    // STARTTLS. Can be relaxed for a trusted local relay with
    // SMTP_STARTTLS_REQUIRED=false.
    props.put("mail.smtp.starttls.required", Boolean.toString(settings.startTlsRequired()));
    // Without these a blocked SMTP port holds the HTTP request thread open.
    props.put("mail.smtp.connectiontimeout", Integer.toString(settings.timeoutMs()));
    props.put("mail.smtp.timeout", Integer.toString(settings.timeoutMs()));
    props.put("mail.smtp.writetimeout", Integer.toString(settings.timeoutMs()));
    return sender;
  }

  @PostConstruct
  void logConfiguration() {
    Settings settings = settings();
    if (!settings.enabled()) {
      log.info("Password reset email disabled (MAIL_ENABLED is not true).");
      return;
    }
    if (!settings.provider().isBlank() && !PRESETS.containsKey(settings.provider())
        && settings.host().isBlank()) {
      log.warn("EMAIL_PROVIDER is not a supported preset and SMTP_SERVER is blank; "
          + "set SMTP_SERVER or use a supported provider name.");
    }
    if (settings.host().isBlank()) {
      log.warn("Password reset email is enabled but SMTP_SERVER is blank; "
          + "reset links cannot be delivered.");
      return;
    }
    if (settings.username().isBlank() != settings.password().isBlank()) {
      log.warn("SMTP authentication is partially configured; set both SMTP_EMAIL and "
          + "SMTP_PASSWORD, or leave both blank for an unauthenticated relay.");
    }
    if (!settings.implicitTls() && !settings.startTls() && !settings.username().isBlank()) {
      log.warn("STARTTLS is disabled while SMTP authentication is enabled; credentials "
          + "would be sent over plaintext. Set SMTP_USE_SSL=true or re-enable STARTTLS.");
    }
    log.info("Password reset email ready via {}:{} with {} and {}",
        settings.host(),
        settings.port(),
        tlsDescription(settings),
        settings.username().isBlank() ? "no SMTP authentication" : "SMTP authentication");
  }

  private static String tlsDescription(Settings settings) {
    if (settings.implicitTls()) {
      return "implicit TLS";
    }
    if (!settings.startTls()) {
      return "no TLS";
    }
    return settings.startTlsRequired() ? "required STARTTLS" : "opportunistic STARTTLS";
  }

  /** Resolved transport settings used to build the SMTP sender. */
  record Settings(
      boolean enabled,
      String provider,
      String host,
      int port,
      String username,
      String password,
      boolean implicitTls,
      boolean startTls,
      boolean startTlsRequired,
      int timeoutMs) {
  }

  private record Preset(String host, int port) {
  }

  Settings settings() {
    String provider = firstText("EMAIL_PROVIDER", "app.mail.provider")
        .toLowerCase(Locale.ROOT);
    Preset preset = PRESETS.get(provider);

    String host = firstText("SMTP_SERVER", "app.mail.host", "spring.mail.host");
    if (host.isBlank() && preset != null) {
      host = preset.host();
    }

    Boolean ssl = bool("SMTP_USE_SSL", "app.mail.use-ssl");
    Integer configuredPort = number("SMTP_PORT", "app.mail.port", "spring.mail.port");
    int port = configuredPort != null
        ? configuredPort
        : Boolean.TRUE.equals(ssl) ? 465
        : preset != null ? preset.port() : 0;

    // Port 465 speaks TLS from the first byte; 587 upgrades with STARTTLS.
    boolean implicitTls = ssl != null ? ssl : port == 465;
    if (port <= 0) {
      port = implicitTls ? 465 : 587;
    }

    boolean startTls = !implicitTls && boolOr(true, "SMTP_STARTTLS", "app.mail.starttls");
    boolean startTlsRequired = startTls
        && boolOr(true, "SMTP_STARTTLS_REQUIRED", "app.mail.starttls-required");

    Integer timeout = positiveInteger("SMTP_TIMEOUT_MS", "app.mail.timeout-ms");
    int timeoutMs = timeout != null && timeout > 0 ? timeout : DEFAULT_TIMEOUT_MS;

    return new Settings(
        Boolean.TRUE.equals(bool("MAIL_ENABLED", "app.mail.enabled")),
        provider,
        host,
        port,
        firstText("SMTP_EMAIL", "app.mail.username", "spring.mail.username"),
        firstText("SMTP_PASSWORD", "app.mail.password", "spring.mail.password"),
        implicitTls,
        startTls,
        startTlsRequired,
        timeoutMs);
  }

  private String firstText(String... keys) {
    for (String key : keys) {
      String value = text(key);
      if (!value.isBlank()) {
        return value;
      }
    }
    return "";
  }

  private String text(String key) {
    String value = env.getProperty(key);
    return value == null ? "" : value.trim();
  }

  /** Blank optional values are ignored, allowing partially filled local configuration. */
  private Boolean bool(String... keys) {
    String value = firstText(keys);
    return value.isBlank() ? null : parseBoolean(keys[0], value);
  }

  private boolean boolOr(boolean defaultValue, String... keys) {
    String value = firstText(keys);
    return value.isBlank() ? defaultValue : parseBoolean(keys[0], value);
  }

  private static boolean parseBoolean(String key, String value) {
    if ("true".equalsIgnoreCase(value)) {
      return true;
    }
    if ("false".equalsIgnoreCase(value)) {
      return false;
    }
    throw new IllegalStateException("Invalid boolean value for " + key + "; use true or false.");
  }

  private Integer number(String... keys) {
    String value = firstText(keys);
    if (value.isBlank()) {
      return null;
    }
    try {
      int port = Integer.parseInt(value);
      if (port < 1 || port > 65535) {
        throw new NumberFormatException();
      }
      return port;
    } catch (NumberFormatException e) {
      throw new IllegalStateException("Invalid SMTP port in " + keys[0]
          + "; provide a number from 1 to 65535.");
    }
  }

  private Integer positiveInteger(String... keys) {
    String value = firstText(keys);
    if (value.isBlank()) {
      return null;
    }
    try {
      int number = Integer.parseInt(value);
      if (number <= 0) {
        throw new NumberFormatException();
      }
      return number;
    } catch (NumberFormatException e) {
      throw new IllegalStateException("Invalid positive integer in " + keys[0] + ".");
    }
  }
}
