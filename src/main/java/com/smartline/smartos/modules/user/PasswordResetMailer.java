package com.smartline.smartos.modules.user;

import com.smartline.smartos.common.mail.BrandedEmailSender;
import com.smartline.smartos.common.mail.EmailBrand;
import com.smartline.smartos.common.mail.EmailContent;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Sends password-reset links through the shared branded email pipeline.
 *
 * <p>Mail is optional: when SMTP is not configured {@link #isEnabled()} is
 * false and {@link PasswordResetService} either uses the explicitly opt-in
 * development token mode or answers with a configuration error. The raw token
 * is never logged.
 */
@Component
public class PasswordResetMailer {

  private final BrandedEmailSender sender;
  private final EmailBrand brand;
  private final String frontendUrl;
  private final long tokenTtlMinutes;

  public PasswordResetMailer(
      BrandedEmailSender sender,
      EmailBrand brand,
      @Value("${app.frontend-url:http://localhost:3000}") String frontendUrl,
      @Value("${app.security.password-reset.token-ttl-minutes:30}") long tokenTtlMinutes) {
    this.sender = sender;
    this.brand = brand;
    this.frontendUrl = frontendUrl;
    this.tokenTtlMinutes = tokenTtlMinutes;
  }

  public boolean isEnabled() {
    return sender.isReady();
  }

  public String resetUrl(String rawToken) {
    String base = frontendUrl.endsWith("/")
        ? frontendUrl.substring(0, frontendUrl.length() - 1)
        : frontendUrl;
    return base + "/reset-password?token="
        + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
  }

  /**
   * @return true when the message was handed to SMTP; false when mail is
   *         disabled or delivery failed. Callers always return the same
   *         generic response, so delivery problems never leak account state.
   */
  public boolean send(AppUser user, String rawToken) {
    if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
      return false;
    }
    EmailContent content = EmailContent.passwordReset(
        brand.appName(), user.getFullName(), resetUrl(rawToken), tokenTtlMinutes);
    return sender.send(user.getEmail(),
        "Reset your " + brand.appName() + " password", content);
  }
}
