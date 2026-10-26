package com.smartline.smartos.modules.user;

import com.smartline.smartos.common.mail.BrandedEmailTemplate;
import com.smartline.smartos.common.mail.BrandedEmailTemplate.EmailContent;
import com.smartline.smartos.common.mail.BrandedEmailTemplate.RenderedEmail;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Sends password-reset links. Mail is optional: when no SMTP host is
 * configured the service reports {@link #isEnabled()} false and the reset
 * service falls back to the explicitly opt-in development token mode.
 *
 * <p>The raw token is never logged by this class.
 */
@Component
public class PasswordResetMailer {

  private static final Logger log = LoggerFactory.getLogger(PasswordResetMailer.class);

  private final ObjectProvider<JavaMailSender> mailSenderProvider;
  private final boolean enabled;
  private final String from;
  private final String frontendUrl;
  private final BrandedEmailTemplate emailTemplate;

  public PasswordResetMailer(
      ObjectProvider<JavaMailSender> mailSenderProvider,
      @Value("${app.mail.enabled:false}") boolean enabled,
      @Value("${app.mail.from:no-reply@smartos.local}") String from,
      @Value("${app.frontend-url:http://localhost:3000}") String frontendUrl,
      @Value("${SMTP_EMAIL:}") String smtpEmail,
      BrandedEmailTemplate emailTemplate) {
    this.mailSenderProvider = mailSenderProvider;
    this.enabled = enabled;
    this.from = from == null || from.isBlank()
        ? smtpEmail == null || smtpEmail.isBlank() ? "no-reply@smartos.local" : smtpEmail.trim()
        : from.trim();
    this.frontendUrl = frontendUrl;
    this.emailTemplate = emailTemplate;
  }

  public boolean isEnabled() {
    JavaMailSender sender = mailSenderProvider.getIfAvailable();
    return enabled && sender != null
        && (!(sender instanceof org.springframework.mail.javamail.JavaMailSenderImpl impl)
            || (impl.getHost() != null && !impl.getHost().isBlank()));
  }

  public String resetUrl(String rawToken) {
    String base = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1)
        : frontendUrl;
    return base + "/reset-password?token=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
  }

  /**
   * @return true when the message was handed to SMTP; false when mail is
   *         disabled or delivery failed. Callers always return the same
   *         generic response, so delivery problems never leak account state.
   */
  public boolean send(AppUser user, String rawToken) {
    if (!enabled) {
      return false;
    }
    JavaMailSender sender = mailSenderProvider.getIfAvailable();
    if (sender == null) {
      log.error("Password reset email is enabled but no mail transport is configured.");
      return false;
    }
    if (sender instanceof org.springframework.mail.javamail.JavaMailSenderImpl implementation
        && (implementation.getHost() == null || implementation.getHost().isBlank())) {
      log.error("Password reset email is enabled but SMTP_SERVER and EMAIL_PROVIDER are blank.");
      return false;
    }
    if (sender instanceof org.springframework.mail.javamail.JavaMailSenderImpl implementation
        && (StringUtils.hasText(implementation.getUsername())
            != StringUtils.hasText(implementation.getPassword()))) {
      log.error("SMTP authentication is incomplete; set both SMTP_EMAIL and SMTP_PASSWORD.");
      return false;
    }
    try {
      MimeMessage message = sender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(
          message, MimeMessageHelper.MULTIPART_MODE_MIXED, "UTF-8");
      helper.setFrom(from);
      helper.setTo(user.getEmail());
      helper.setSubject("Reset your SmartOS password");
      String name = user.getFullName() == null || user.getFullName().isBlank()
          ? "there" : user.getFullName().trim();
      String link = resetUrl(rawToken);
      RenderedEmail rendered = emailTemplate.render(new EmailContent(
          "Choose a new password for your SmartOS account.",
          "Reset your password",
          "Hello " + name + ",",
          List.of("We received a request to reset the password for your SmartOS account.",
              "Use the button below to choose a new password. This single-use link expires "
                  + "in 30 minutes."),
          "Reset password",
          link,
          "Security notice",
          "If you did not request a password reset, you can safely ignore this email. "
              + "Your password will not change."));
      helper.setText(rendered.text(), rendered.html());
      sender.send(message);
      return true;
    } catch (MailAuthenticationException e) {
      log.error("Could not authenticate with the SMTP server; verify SMTP_EMAIL and "
          + "SMTP_PASSWORD.");
      return false;
    } catch (MailSendException e) {
      if (hasCause(e, AuthenticationFailedException.class)
          || hasCause(e, MailAuthenticationException.class)) {
        log.error("Could not authenticate with the SMTP server; verify SMTP_EMAIL and "
            + "SMTP_PASSWORD.");
      } else {
        log.error("Could not deliver password reset email; verify SMTP_SERVER, SMTP_PORT, "
            + "and the server's TLS settings.");
      }
      return false;
    } catch (MailException e) {
      log.error("The SMTP provider rejected the password reset email ({}).",
          e.getClass().getSimpleName());
      return false;
    } catch (MessagingException e) {
      log.error("Could not construct the password reset email ({}).",
          e.getClass().getSimpleName());
      return false;
    }
  }

  private static boolean hasCause(Throwable failure, Class<? extends Throwable> causeType) {
    Throwable cause = failure.getCause();
    while (cause != null && cause != failure) {
      if (causeType.isInstance(cause)) {
        return true;
      }
      failure = cause;
      cause = cause.getCause();
    }
    return false;
  }
}
