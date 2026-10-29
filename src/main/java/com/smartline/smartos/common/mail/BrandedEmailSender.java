package com.smartline.smartos.common.mail;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import javax.net.ssl.SSLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * The single outbound path for transactional email.
 *
 * <p>Every message gets the same branded HTML/plain-text MIME pair, the same
 * sender resolution and the same secret-free error classification. New email
 * features only need to build an {@link EmailContent}; they never assemble a
 * {@code MimeMessage} or talk to SMTP directly.
 *
 * <p>Logging policy: the recipient is masked ({@code a***@example.com}), the
 * subject is safe, and neither the raw token nor the full action URL is ever
 * written. Provider responses are reduced to exception type names.
 */
@Component
public class BrandedEmailSender {

  private static final Logger log = LoggerFactory.getLogger(BrandedEmailSender.class);
  private static final int MAX_CAUSE_DEPTH = 8;

  private final ObjectProvider<JavaMailSender> mailSenderProvider;
  private final BrandedEmailTemplate template;
  private final boolean enabled;
  private final String configuredFrom;
  private final String replyTo;

  public BrandedEmailSender(
      ObjectProvider<JavaMailSender> mailSenderProvider,
      BrandedEmailTemplate template,
      @Value("${app.mail.enabled:false}") boolean enabled,
      @Value("${app.mail.from:}") String configuredFrom,
      @Value("${app.mail.brand.support-email:}") String replyTo) {
    this.mailSenderProvider = mailSenderProvider;
    this.template = template;
    this.enabled = enabled;
    this.configuredFrom = configuredFrom;
    this.replyTo = replyTo;
  }

  /**
   * True only when mail is enabled and the transport has enough configuration
   * to attempt a send. A blank host or half-set credentials are not "ready",
   * so callers can fail fast instead of pretending a message was sent.
   */
  public boolean isReady() {
    JavaMailSender sender = mailSenderProvider.getIfAvailable();
    return enabled && sender != null && readinessProblem(sender) == null;
  }

  /**
   * Renders and sends one branded email.
   *
   * @return true when SMTP accepted the message; false when mail is disabled,
   *         misconfigured, or delivery failed. The caller decides what to log
   *         or return to the user; this method never throws.
   */
  public boolean send(String to, String subject, EmailContent content) {
    Objects.requireNonNull(content, "content");
    if (!hasText(to)) {
      log.warn("Skipped '{}': no recipient address.", subject);
      return false;
    }
    if (!enabled) {
      log.warn("Skipped '{}': MAIL_ENABLED is not true.", subject);
      return false;
    }
    JavaMailSender sender = mailSenderProvider.getIfAvailable();
    if (sender == null) {
      log.error("Cannot send '{}': no JavaMailSender is available.", subject);
      return false;
    }
    String problem = readinessProblem(sender);
    if (problem != null) {
      log.error("Cannot send '{}': {}.", subject, problem);
      return false;
    }

    String from = resolveFrom(sender);
    try {
      // Every message has a plain-text and an HTML alternative, so the helper
      // must be created in multipart mode. Setting alternative texts on a
      // single-part helper throws IllegalStateException ("Not in multipart
      // mode") at send time, after the caller has already saved its work.
      BrandedEmailTemplate.RenderedEmail rendered = template.render(content);
      MimeMessage message = sender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(
          message, true, StandardCharsets.UTF_8.name());
      helper.setFrom(from);
      helper.setTo(to);
      if (hasText(replyTo) && !replyTo.equalsIgnoreCase(from)) {
        helper.setReplyTo(replyTo);
      }
      helper.setSubject(subject);
      helper.setSentDate(new Date());
      helper.setText(rendered.text(), rendered.html());
      message.setHeader("Auto-Submitted", "auto-generated");
      message.setHeader("X-Auto-Response-Suppress", "OOF, AutoReply");
      sender.send(message);
      log.debug("Sent '{}' to {}", subject, mask(to));
      return true;
    } catch (MailAuthenticationException e) {
      logAuthenticationFailure(subject, to);
      return false;
    } catch (MailSendException e) {
      if (hasCause(e, AuthenticationFailedException.class)
          || hasCause(e, MailAuthenticationException.class)) {
        logAuthenticationFailure(subject, to);
      } else if (isConnectivityFailure(e)) {
        log.error("Could not connect to the SMTP server at {} while sending '{}'; check "
            + "SMTP_SERVER, SMTP_PORT, the TLS settings and outbound firewall access.",
            endpoint(sender), subject);
      } else {
        log.error("The SMTP server rejected '{}' for {} ({}); check the sender address "
            + "(MAIL_FROM must normally match SMTP_EMAIL) and provider sending limits.",
            subject, mask(to), rootType(e));
      }
      return false;
    } catch (MailParseException | MailPreparationException e) {
      log.error("Could not build the email '{}' ({}).", subject, e.getClass().getSimpleName(), e);
      return false;
    } catch (MailException e) {
      log.error("Could not send '{}' to {} ({}).", subject, mask(to),
          e.getClass().getSimpleName(), e);
      return false;
    } catch (MessagingException e) {
      log.error("Could not assemble '{}' ({}).", subject, e.getClass().getSimpleName(), e);
      return false;
    } catch (RuntimeException e) {
      // The contract of this method is that it never throws: a template or
      // message-assembly problem is a delivery failure like any other, not a
      // reason to fail the operation that triggered the email. The cause is
      // still logged in full so it stays diagnosable.
      log.error("Could not assemble the email '{}' ({}).", subject,
          e.getClass().getSimpleName(), e);
      return false;
    }
  }

  private String readinessProblem(JavaMailSender sender) {
    if (!(sender instanceof JavaMailSenderImpl implementation)) {
      return null;
    }
    if (!hasText(implementation.getHost())) {
      return "SMTP_SERVER and EMAIL_PROVIDER are blank";
    }
    boolean username = hasText(implementation.getUsername());
    boolean password = hasText(implementation.getPassword());
    if (username != password) {
      return "only one of SMTP_EMAIL and SMTP_PASSWORD is set";
    }
    return null;
  }

  /**
   * Providers reject a From address that differs from the authenticated
   * mailbox. When MAIL_FROM is unset, or still has the placeholder
   * {@code smartos.local} domain, use the SMTP username instead.
   */
  private String resolveFrom(JavaMailSender sender) {
    String candidate = hasText(configuredFrom) ? configuredFrom.trim() : "";
    String username = sender instanceof JavaMailSenderImpl implementation
        ? implementation.getUsername() : null;
    if (candidate.isEmpty()
        || (candidate.toLowerCase(Locale.ROOT).endsWith("@smartos.local") && hasText(username))) {
      if (hasText(username)) {
        candidate = username.trim();
      }
    }
    return candidate.isEmpty() ? "no-reply@smartos.local" : candidate;
  }

  private static void logAuthenticationFailure(String subject, String to) {
    log.error("SMTP authentication failed while sending '{}' to {}; verify SMTP_EMAIL and "
        + "SMTP_PASSWORD. Gmail and Microsoft 365 require an app password when MFA is on.",
        subject, mask(to));
  }

  private static boolean isConnectivityFailure(Throwable failure) {
    return hasCause(failure, ConnectException.class)
        || hasCause(failure, UnknownHostException.class)
        || hasCause(failure, SocketTimeoutException.class)
        || hasCause(failure, NoRouteToHostException.class)
        || hasCause(failure, SocketException.class)
        || hasCause(failure, SSLException.class);
  }

  private static boolean hasCause(Throwable failure, Class<? extends Throwable> type) {
    Throwable current = failure.getCause();
    int depth = 0;
    while (current != null && current != failure && depth++ < MAX_CAUSE_DEPTH) {
      if (type.isInstance(current)) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  private static String rootType(Throwable failure) {
    Throwable current = failure;
    int depth = 0;
    while (current.getCause() != null && current.getCause() != current
        && depth++ < MAX_CAUSE_DEPTH) {
      current = current.getCause();
    }
    return current.getClass().getSimpleName();
  }

  private static String endpoint(JavaMailSender sender) {
    if (sender instanceof JavaMailSenderImpl implementation && hasText(implementation.getHost())) {
      return implementation.getHost() + ":" + implementation.getPort();
    }
    return "the configured SMTP server";
  }

  private static String mask(String email) {
    int at = email.indexOf('@');
    if (at <= 0) {
      return "the recipient";
    }
    return email.charAt(0) + "***@" + email.substring(at + 1);
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
