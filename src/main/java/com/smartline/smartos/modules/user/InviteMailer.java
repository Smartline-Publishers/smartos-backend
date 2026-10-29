package com.smartline.smartos.modules.user;

import com.smartline.smartos.common.mail.BrandedEmailSender;
import com.smartline.smartos.common.mail.EmailBrand;
import com.smartline.smartos.common.mail.EmailContent;
import com.smartline.smartos.modules.organization.Organization;
import com.smartline.smartos.security.Role;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Sends organization invitation links through the shared branded email pipeline.
 *
 * <p>Mail is optional: when SMTP is not configured {@link #isEnabled()} is
 * false and the invitation is still created so the inviter can copy the link
 * and share it through other channels. The raw token is never logged.
 */
@Component
public class InviteMailer {

  private final BrandedEmailSender sender;
  private final EmailBrand brand;
  private final String frontendUrl;

  public InviteMailer(BrandedEmailSender sender, EmailBrand brand,
      @Value("${app.frontend-url:http://localhost:3000}") String frontendUrl) {
    this.sender = sender;
    this.brand = brand;
    this.frontendUrl = frontendUrl;
  }

  public boolean isEnabled() {
    return sender.isReady();
  }

  /**
   * The full accept URL handed to the invitee. Points at the frontend's
   * invitation acceptance flow ({@code /invite/accept?token=...}).
   */
  public String acceptUrl(String rawToken) {
    String base = frontendUrl.endsWith("/")
        ? frontendUrl.substring(0, frontendUrl.length() - 1)
        : frontendUrl;
    return base + "/invite/accept?token="
        + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
  }

  /**
   * @return true when the message was handed to SMTP; false when mail is
   *         disabled or delivery failed. The invitation itself is always
   *         created, so a delivery problem never blocks the inviter: they can
   *         copy the link and share it manually.
   */
  public boolean send(Organization org, String email, String rawToken, Role role, int daysValid) {
    if (org == null || email == null || email.isBlank() || rawToken == null || rawToken.isBlank()) {
      return false;
    }
    String orgName = org.getName() == null || org.getName().isBlank()
        ? "an organisation" : org.getName();
    String roleLabel = role == null ? "member"
        : role.name().replace('_', ' ').toLowerCase(Locale.ROOT);
    String expires = duration(daysValid);
    EmailContent content = EmailContent.builder("You've been invited to " + orgName)
        .preheader("Accept the invitation to join " + orgName + " on " + brand.appName() + ".")
        .eyebrow("ORGANIZATION INVITATION")
        .subtitle(orgName + " has invited you to join their organisation on " + brand.appName() + ".")
        .greeting("Hello there,")
        .paragraph("You have been invited to join " + orgName + " with the " + roleLabel + " role.")
        .paragraph("Open the link below to create your account and accept the invitation.")
        .action("Accept invitation", acceptUrl(rawToken),
            "This one-time link expires in " + expires + " and can only be used once.")
        .securityNotice("Security notice",
            "Only use this link if you were expecting an invitation from " + orgName + ". "
                + "Do not forward this email to anyone else.")
        .footerNote("You received this email because " + orgName
            + " added this address to their organisation on " + brand.appName() + ".")
        .build();
    return sender.send(email, "You've been invited to " + orgName, content);
  }

  private static String duration(int days) {
    if (days <= 0) {
      return "soon";
    }
    return days == 1 ? "1 day" : days + " days";
  }
}
