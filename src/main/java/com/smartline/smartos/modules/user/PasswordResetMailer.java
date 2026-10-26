package com.smartline.smartos.modules.user;

import jakarta.mail.internet.MimeMessage;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

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

  public PasswordResetMailer(
      ObjectProvider<JavaMailSender> mailSenderProvider,
      @Value("${app.mail.enabled:false}") boolean enabled,
      @Value("${app.mail.from:no-reply@smartos.local}") String from,
      @Value("${app.frontend-url:http://localhost:3000}") String frontendUrl) {
    this.mailSenderProvider = mailSenderProvider;
    this.enabled = enabled;
    this.from = from;
    this.frontendUrl = frontendUrl;
  }

  public boolean isEnabled() {
    return enabled && mailSenderProvider.getIfAvailable() != null;
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
      log.warn("Password reset mail is enabled but no JavaMailSender is configured; "
          + "set SPRING_MAIL_HOST (and credentials) to deliver reset emails.");
      return false;
    }
    try {
      MimeMessage message = sender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
      helper.setFrom(from);
      helper.setTo(user.getEmail());
      helper.setSubject("Reset your SmartOS password");
      String name = user.getFullName() == null || user.getFullName().isBlank()
          ? "there" : user.getFullName().trim();
      String link = resetUrl(rawToken);
      String text = "Hello " + name + ",\n\n"
          + "We received a request to reset the password for your SmartOS account.\n"
          + "Open this single-use link within 30 minutes to choose a new password:\n\n"
          + link + "\n\n"
          + "If you did not request this, you can safely ignore this email; your password "
          + "will not change.\n\n"
          + "— SmartOS\n";
      helper.setText(text, false);
      sender.send(message);
      return true;
    } catch (Exception e) {
      // Deliberately do not include the token or the link in the log.
      log.error("Could not send password reset email: {}", e.getMessage());
      return false;
    }
  }
}
