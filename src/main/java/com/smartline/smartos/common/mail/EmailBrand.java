package com.smartline.smartos.common.mail;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Brand values shared by every outgoing email.
 *
 * <p>Keeping them in one record means a logo, colour, or support address change
 * is made once and applies to password resets, verification mail, welcome mail
 * and notifications. Values are resolved from {@code app.mail.brand.*} (see
 * {@code application.yml}) and validated here so a malformed colour or URL can
 * never break out of its HTML attribute.
 *
 * <p>Colours intentionally mirror the web app tokens: red primary
 * ({@code --org-brand-600}), blue tertiary ({@code --org-tertiary-600}), ink
 * text and the paper page background.
 */
public record EmailBrand(
    String appName,
    String companyName,
    String eyebrow,
    String logoUrl,
    String primaryColor,
    String accentColor,
    String websiteUrl,
    String supportEmail,
    String supportPhone,
    String supportHours,
    String footerAddress,
    String privacyUrl,
    String termsUrl,
    String helpUrl) {

  private static final Pattern HEX_COLOR =
      Pattern.compile("^#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6})$");
  private static final Pattern EMAIL =
      Pattern.compile("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$");

  public EmailBrand {
    appName = textOr(appName, "SmartOS");
    companyName = textOr(companyName, "Smartline Publishers Ltd");
    eyebrow = textOr(eyebrow, "SMARTLINE PUBLISHERS");
    logoUrl = httpUrl(logoUrl);
    primaryColor = colorOr(primaryColor, "#dc2626");
    accentColor = colorOr(accentColor, "#2563eb");
    websiteUrl = stripTrailingSlash(textOr(websiteUrl, "http://localhost:3000"));
    supportEmail = EMAIL.matcher(textOr(supportEmail, "")).matches()
        ? textOr(supportEmail, "").toLowerCase(Locale.ROOT) : "";
    supportPhone = textOr(supportPhone, "");
    supportHours = textOr(supportHours, "");
    footerAddress = textOr(footerAddress, "");
    // Transactional emails always link back to the public legal/help pages.
    privacyUrl = httpUrlOr(privacyUrl, websiteUrl + "/legal?doc=privacy");
    termsUrl = httpUrlOr(termsUrl, websiteUrl + "/legal?doc=terms");
    helpUrl = httpUrlOr(helpUrl, websiteUrl + "/help");
  }

  /** Safe defaults used by tests and by any caller that has no configuration. */
  public static EmailBrand defaults() {
    return new EmailBrand("SmartOS", "Smartline Publishers Ltd", "SMARTLINE PUBLISHERS",
        "", "#dc2626", "#2563eb", "http://localhost:3000",
        "", "", "", "", "", "", "");
  }

  private static String textOr(String value, String fallback) {
    String trimmed = value == null ? "" : value.replaceAll("[\\r\\n\\t]", " ").trim();
    return trimmed.isEmpty() ? fallback : trimmed;
  }

  private static String stripTrailingSlash(String value) {
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
  }

  private static String colorOr(String value, String fallback) {
    String candidate = textOr(value, "");
    return HEX_COLOR.matcher(candidate).matches() ? candidate : fallback;
  }

  private static String httpUrl(String value) {
    String candidate = textOr(value, "");
    return isHttpUrl(candidate) ? candidate : "";
  }

  private static String httpUrlOr(String value, String fallback) {
    String candidate = textOr(value, "");
    return isHttpUrl(candidate) ? candidate : fallback;
  }

  private static boolean isHttpUrl(String value) {
    String lower = value.toLowerCase(Locale.ROOT);
    return lower.startsWith("https://") || lower.startsWith("http://");
  }
}
