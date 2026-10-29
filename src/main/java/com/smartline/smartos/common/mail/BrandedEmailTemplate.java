package com.smartline.smartos.common.mail;

import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Renders the shared SmartOS email shell plus a plain-text alternative.
 *
 * <p>Design goals:
 * <ul>
 *   <li>One layout for every outgoing email. Content is supplied through
 *       {@link EmailContent}; branding comes from {@link EmailBrand}.</li>
 *   <li>Table-based, inline-styled HTML that survives Gmail, Apple Mail,
 *       Outlook and Yahoo. A {@code <style>} block adds responsive and
 *       dark-mode rules, but every light-mode colour is also inline so the
 *       email still looks correct when a client strips styles.</li>
 *   <li>Dark mode via {@code prefers-color-scheme} and Outlook.com's
 *       {@code [data-ogsc]}/{@code [data-ogsb]} markers, with the light
 *       palette as the universal fallback.</li>
 *   <li>All dynamic text is HTML-escaped and URLs are restricted to
 *       http(s)/mailto, so content can never inject markup.</li>
 * </ul>
 */
@Component
public class BrandedEmailTemplate {

  /** Matches the web app's Inter-first stack, with broadly available fallbacks. */
  private static final String FONT =
      "Inter,-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif";

  private final EmailBrand brand;

  public BrandedEmailTemplate(EmailBrand brand) {
    this.brand = Objects.requireNonNull(brand, "brand");
  }

  public RenderedEmail render(EmailContent content) {
    Objects.requireNonNull(content, "content");
    return new RenderedEmail(html(content), text(content));
  }

  // ---------------------------------------------------------------------
  // HTML
  // ---------------------------------------------------------------------

  private String html(EmailContent content) {
    StringBuilder html = new StringBuilder(14_000);
    html.append("<!doctype html>\n")
        .append("<html lang=\"en\" dir=\"ltr\">\n<head>\n")
        .append("<meta charset=\"utf-8\">\n")
        .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
        .append("<meta name=\"x-apple-disable-message-reformatting\">\n")
        .append("<meta name=\"color-scheme\" content=\"light dark\">\n")
        .append("<meta name=\"supported-color-schemes\" content=\"light dark\">\n")
        .append("<meta name=\"format-detection\" content=\"telephone=no,address=no,email=no,date=no\">\n")
        .append("<title>").append(escape(content.title())).append("</title>\n")
        .append("<!--[if mso]><xml><o:OfficeDocumentSettings>")
        .append("<o:PixelsPerInch>96</o:PixelsPerInch>")
        .append("</o:OfficeDocumentSettings></xml><![endif]-->\n")
        .append(styles())
        .append("</head>\n")
        .append("<body class=\"email-page\" style=\"margin:0;padding:0;width:100%;")
        .append("background-color:#eef1f6;-webkit-text-size-adjust:100%;")
        .append("-ms-text-size-adjust:100%;\">\n")
        .append(preheader(content.preheader()))
        .append("<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" ")
        .append("border=\"0\" class=\"email-page\" bgcolor=\"#eef1f6\" ")
        .append("style=\"width:100%;background-color:#eef1f6;\">\n")
        .append("<tr><td class=\"email-shell\" align=\"center\" style=\"padding:40px 16px;\">\n")
        .append(card(content))
        .append("</td></tr>\n</table>\n")
        .append("</body>\n</html>\n");
    return html.toString();
  }

  private String card(EmailContent content) {
    StringBuilder card = new StringBuilder(10_000);
    card.append("<table role=\"presentation\" width=\"600\" cellspacing=\"0\" cellpadding=\"0\" ")
        .append("border=\"0\" class=\"email-card\" bgcolor=\"#ffffff\" ")
        .append("style=\"width:100%;max-width:600px;background-color:#ffffff;")
        .append("border-radius:14px;overflow:hidden;\">\n")
        .append(header())
        .append("<tr><td class=\"email-content\" bgcolor=\"#ffffff\" ")
        .append("style=\"padding:22px 40px 40px;background-color:#ffffff;")
        .append("font-family:").append(FONT).append(";\">\n")
        .append(intro(content))
        .append("</td></tr>\n")
        .append(help())
        .append(footer(content))
        .append("</table>\n");
    return card.toString();
  }

  private String header() {
    StringBuilder header = new StringBuilder(2_000);
    header.append("<tr><td class=\"email-header\" align=\"center\" bgcolor=\"#ffffff\" ")
        .append("style=\"padding:36px 40px 8px;background-color:#ffffff;\">\n");

    if (!brand.logoUrl().isEmpty()) {
      header.append("<a href=\"").append(attr(safeUrl(brand.websiteUrl())))
          .append("\" target=\"_blank\" rel=\"noopener\" style=\"text-decoration:none;\">")
          .append("<img class=\"email-logo\" src=\"").append(attr(brand.logoUrl()))
          .append("\" alt=\"").append(attr(brand.appName()))
          .append("\" height=\"44\" style=\"display:block;height:44px;width:auto;")
          .append("max-width:240px;border:0;outline:none;text-decoration:none;")
          .append("border-radius:8px;background-color:#ffffff;\">")
          .append("</a>\n");
    } else {
      String wordmark = escape(brand.appName());
      if (brand.appName().endsWith("OS") && brand.appName().length() > 2) {
        wordmark = escape(brand.appName().substring(0, brand.appName().length() - 2))
            + "<span class=\"email-os\" style=\"color:" + attr(brand.accentColor())
            + ";\">OS</span>";
      }
      header.append("<table role=\"presentation\" cellspacing=\"0\" cellpadding=\"0\" ")
          .append("border=\"0\" align=\"center\" style=\"margin:0 auto;\">\n<tr>\n")
          .append("<td width=\"44\" height=\"44\" align=\"center\" valign=\"middle\" ")
          .append("class=\"email-mark\" bgcolor=\"").append(attr(brand.accentColor()))
          .append("\" style=\"width:44px;height:44px;border-radius:12px;background-color:")
          .append(attr(brand.accentColor())).append(";font-family:").append(FONT)
          .append(";font-size:23px;font-weight:800;line-height:44px;\">")
          .append("<a class=\"email-mark-text\" href=\"").append(attr(safeUrl(brand.websiteUrl())))
          .append("\" target=\"_blank\" rel=\"noopener\" style=\"display:block;color:#ffffff;")
          .append("text-decoration:none;font-family:").append(FONT)
          .append(";font-size:23px;font-weight:800;line-height:44px;\">S</a>")
          .append("</td>\n")
          .append("<td style=\"padding-left:12px;text-align:left;\">\n")
          .append("<div class=\"email-wordmark\" style=\"font-family:").append(FONT)
          .append(";font-size:22px;font-weight:800;line-height:1.1;letter-spacing:-0.6px;")
          .append("color:#111827;\">").append(wordmark).append("</div>\n")
          .append("<div class=\"email-tagline\" style=\"margin-top:5px;font-family:").append(FONT)
          .append(";font-size:10px;font-weight:600;line-height:1.2;letter-spacing:1.6px;")
          .append("text-transform:uppercase;color:#64748b;\">")
          .append(escape(brand.eyebrow())).append("</div>\n")
          .append("</td>\n</tr>\n</table>\n");
    }

    header.append("</td></tr>\n");
    return header.toString();
  }

  private String intro(EmailContent content) {
    StringBuilder intro = new StringBuilder(6_000);

    if (!content.eyebrow().isEmpty()) {
      intro.append("<p class=\"email-eyebrow\" style=\"margin:0 0 10px;text-align:center;")
          .append("font-family:").append(FONT).append(";font-size:11px;font-weight:700;")
          .append("line-height:1.4;letter-spacing:1.6px;text-transform:uppercase;color:")
          .append(attr(brand.accentColor())).append(";\">")
          .append(escape(content.eyebrow())).append("</p>\n");
    }

    intro.append("<h1 class=\"email-title\" style=\"margin:0 0 10px;text-align:center;")
        .append("font-family:").append(FONT).append(";font-size:26px;font-weight:800;")
        .append("line-height:1.25;letter-spacing:-0.5px;color:#111827;\">")
        .append(escape(content.title())).append("</h1>\n");

    if (!content.subtitle().isEmpty()) {
      intro.append("<p class=\"email-subtitle\" style=\"margin:0 0 28px;text-align:center;")
          .append("font-family:").append(FONT).append(";font-size:15px;line-height:1.6;")
          .append("color:#5b6b82;\">").append(escape(content.subtitle())).append("</p>\n");
    } else {
      intro.append("<div style=\"height:18px;line-height:18px;font-size:18px;\">&nbsp;</div>\n");
    }

    if (!content.greeting().isEmpty()) {
      intro.append("<p class=\"email-body\" style=\"margin:0 0 14px;font-family:").append(FONT)
          .append(";font-size:15px;line-height:1.7;color:#374151;\">")
          .append(escape(content.greeting())).append("</p>\n");
    }
    for (String paragraph : content.paragraphs()) {
      intro.append("<p class=\"email-body\" style=\"margin:0 0 14px;font-family:").append(FONT)
          .append(";font-size:15px;line-height:1.7;color:#374151;\">")
          .append(escape(paragraph)).append("</p>\n");
    }

    if (content.action() != null) {
      intro.append(action(content.action()));
    }
    if (content.notice() != null) {
      intro.append(notice(content.notice()));
    }
    return intro.toString();
  }

  private String action(EmailContent.Action action) {
    String url = safeUrl(action.url());
    StringBuilder block = new StringBuilder(2_000);
    block.append("<table role=\"presentation\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\" ")
        .append("align=\"center\" class=\"email-button-table\" style=\"margin:26px auto 0;\">\n")
        .append("<tr><td align=\"center\" class=\"email-button-cell\" bgcolor=\"")
        .append(attr(brand.primaryColor())).append("\" style=\"border-radius:12px;")
        .append("background-color:").append(attr(brand.primaryColor())).append(";\">\n")
        .append("<a class=\"email-button\" href=\"").append(attr(url))
        .append("\" target=\"_blank\" rel=\"noopener\" style=\"display:inline-block;")
        .append("padding:14px 30px;font-family:").append(FONT)
        .append(";font-size:15px;font-weight:700;line-height:1.2;color:#ffffff;")
        .append("text-decoration:none;border-radius:12px;\">")
        .append(escape(action.label())).append("</a>\n")
        .append("</td></tr>\n</table>\n")
        .append("<p class=\"email-muted\" style=\"margin:18px 0 6px;text-align:center;")
        .append("font-family:").append(FONT).append(";font-size:12px;line-height:1.6;")
        .append("color:#5b6b82;\">Or copy and paste this link into your browser:</p>\n")
        .append("<p style=\"margin:0;text-align:center;font-family:").append(FONT)
        .append(";font-size:12px;line-height:1.6;word-break:break-all;\">")
        .append("<a class=\"email-link\" href=\"").append(attr(url))
        .append("\" target=\"_blank\" rel=\"noopener\" style=\"color:#1d4ed8;")
        .append("text-decoration:underline;word-break:break-all;\">")
        .append(escape(url)).append("</a></p>\n");

    if (!action.hint().isEmpty()) {
      block.append("<p class=\"email-muted\" style=\"margin:10px 0 0;text-align:center;")
          .append("font-family:").append(FONT).append(";font-size:12px;line-height:1.6;")
          .append("color:#5b6b82;\">").append(escape(action.hint())).append("</p>\n");
    }
    return block.toString();
  }

  private String notice(EmailContent.Notice notice) {
    String accent = switch (notice.tone()) {
      case SECURITY -> brand.primaryColor();
      case WARNING -> "#b45309";
      case INFO -> brand.accentColor();
    };
    StringBuilder block = new StringBuilder(1_500);
    block.append("<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" ")
        .append("border=\"0\" class=\"email-notice\" style=\"margin:26px 0 0;width:100%;")
        .append("background-color:#f6f8fb;border:1px solid #dbe3ee;border-radius:12px;\">\n")
        .append("<tr><td width=\"4\" bgcolor=\"").append(attr(accent))
        .append("\" style=\"width:4px;background-color:").append(attr(accent))
        .append(";border-radius:12px 0 0 12px;\">&nbsp;</td>\n")
        .append("<td style=\"padding:13px 16px;\">\n");
    if (!notice.title().isEmpty()) {
      block.append("<div class=\"email-notice-title\" style=\"font-family:").append(FONT)
          .append(";font-size:12px;font-weight:700;line-height:1.5;letter-spacing:0.6px;")
          .append("text-transform:uppercase;color:#1f2937;\">")
          .append(escape(notice.title())).append("</div>\n");
    }
    if (!notice.text().isEmpty()) {
      block.append("<div class=\"email-notice-text\" style=\"margin-top:5px;font-family:")
          .append(FONT).append(";font-size:13px;line-height:1.6;color:#46556b;\">")
          .append(escape(notice.text())).append("</div>\n");
    }
    block.append("</td></tr>\n</table>\n");
    return block.toString();
  }

  private String help() {
    boolean email = !brand.supportEmail().isEmpty();
    boolean phone = !brand.supportPhone().isEmpty();
    boolean hours = !brand.supportHours().isEmpty();
    boolean helpUrl = !brand.helpUrl().isEmpty();
    if (!email && !phone && !hours && !helpUrl) {
      return "";
    }

    StringBuilder block = new StringBuilder(2_000);
    block.append("<tr><td class=\"email-help\" bgcolor=\"#ffffff\" style=\"padding:26px 40px 34px;")
        .append("background-color:#ffffff;border-top:1px solid #e4eaf2;\">\n")
        .append("<h2 class=\"email-title\" style=\"margin:0 0 6px;font-family:").append(FONT)
        .append(";font-size:16px;font-weight:700;line-height:1.4;color:#111827;\">Need help?</h2>\n")
        .append("<p class=\"email-muted\" style=\"margin:0 0 12px;font-family:").append(FONT)
        .append(";font-size:13px;line-height:1.6;color:#5b6b82;\">")
        .append("Our support team is happy to help.</p>\n")
        .append("<table role=\"presentation\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\">\n");

    if (email) {
      helpRow(block, "Email", "<a class=\"email-link\" href=\"mailto:")
          .append(attr(brand.supportEmail())).append("\" style=\"color:#1d4ed8;")
          .append("text-decoration:underline;\">").append(escape(brand.supportEmail()))
          .append("</a>");
    }
    if (phone) {
      helpRow(block, "Phone", "<a class=\"email-link\" href=\"")
          .append(attr(telHref(brand.supportPhone()))).append("\" style=\"color:#1d4ed8;")
          .append("text-decoration:underline;\">").append(escape(brand.supportPhone()))
          .append("</a>");
    }
    if (hours) {
      helpRow(block, "Hours", escape(brand.supportHours()));
    }
    if (helpUrl) {
      helpRow(block, "Help centre", "<a class=\"email-link\" href=\"")
          .append(attr(safeUrl(brand.helpUrl()))).append("\" target=\"_blank\" rel=\"noopener\" ")
          .append("style=\"color:#1d4ed8;text-decoration:underline;\">Visit the Help Centre</a>");
    }

    block.append("</table>\n</td></tr>\n");
    return block.toString();
  }

  private StringBuilder helpRow(StringBuilder block, String label, String valueHtml) {
    block.append("<tr>\n")
        .append("<td class=\"email-label\" valign=\"top\" style=\"padding:3px 14px 3px 0;")
        .append("font-family:").append(FONT).append(";font-size:12px;font-weight:600;")
        .append("line-height:1.6;color:#5b6b82;white-space:nowrap;\">")
        .append(escape(label)).append("</td>\n")
        .append("<td class=\"email-body\" valign=\"top\" style=\"padding:3px 0;font-family:")
        .append(FONT).append(";font-size:13px;line-height:1.6;color:#374151;\">")
        .append(valueHtml).append("</td>\n")
        .append("</tr>\n");
    return block;
  }

  private String footer(EmailContent content) {
    StringBuilder footer = new StringBuilder(2_000);
    footer.append("<tr><td class=\"email-footer\" align=\"center\" bgcolor=\"#f8fafc\" ")
        .append("style=\"padding:24px 40px 26px;background-color:#f8fafc;")
        .append("border-top:1px solid #e4eaf2;font-family:").append(FONT)
        .append(";font-size:12px;line-height:1.7;color:#5b6b82;\">\n")
        .append("<div class=\"email-muted\" style=\"color:#5b6b82;\">This is an automated ")
        .append("message from ").append(escape(brand.appName()))
        .append(". Please do not reply.</div>\n");

    if (!content.footerNote().isEmpty()) {
      footer.append("<div class=\"email-muted\" style=\"margin-top:6px;color:#5b6b82;\">")
          .append(escape(content.footerNote())).append("</div>\n");
    }

    footer.append("<div class=\"email-muted\" style=\"margin-top:10px;color:#5b6b82;\">")
        .append("&copy; ").append(Year.now().getValue()).append(" ")
        .append(escape(brand.companyName())).append(". All rights reserved.</div>\n");

    if (!brand.footerAddress().isEmpty()) {
      footer.append("<div class=\"email-muted\" style=\"margin-top:4px;color:#5b6b82;\">")
          .append(escape(brand.footerAddress())).append("</div>\n");
    }

    String links = footerLinks();
    if (!links.isEmpty()) {
      footer.append("<div style=\"margin-top:12px;\">").append(links).append("</div>\n");
    }

    footer.append("</td></tr>\n");
    return footer.toString();
  }

  private String footerLinks() {
    List<String> links = new ArrayList<>();
    if (!brand.helpUrl().isEmpty()) {
      links.add(footerLink("Help centre", brand.helpUrl()));
    }
    if (!brand.privacyUrl().isEmpty()) {
      links.add(footerLink("Privacy", brand.privacyUrl()));
    }
    if (!brand.termsUrl().isEmpty()) {
      links.add(footerLink("Terms", brand.termsUrl()));
    }
    String separator = "<span class=\"email-muted\" style=\"color:#5b6b82;\">"
        + "&nbsp;&middot;&nbsp;</span>";
    return String.join(separator, links);
  }

  private String footerLink(String label, String url) {
    return "<a class=\"email-link\" href=\"" + attr(safeUrl(url))
        + "\" target=\"_blank\" rel=\"noopener\" style=\"color:#1d4ed8;"
        + "text-decoration:underline;\">" + escape(label) + "</a>";
  }

  private static String preheader(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    return "<div class=\"email-preheader\" style=\"display:none;font-size:1px;line-height:1px;"
        + "max-height:0;max-width:0;opacity:0;overflow:hidden;mso-hide:all;\">"
        + escape(value.trim())
        + "&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;</div>\n";
  }

  // ---------------------------------------------------------------------
  // CSS: light inline fallback + responsive + dark mode
  // ---------------------------------------------------------------------

  private static String styles() {
    StringBuilder css = new StringBuilder(4_000);
    css.append("<style>\n")
        .append(":root { color-scheme: light dark; supported-color-schemes: light dark; }\n")
        .append("html, body { margin:0 !important; padding:0 !important; width:100% !important; }\n")
        .append("body { -webkit-text-size-adjust:100%; -ms-text-size-adjust:100%; ")
        .append("background-color:#eef1f6; }\n")
        .append("table { border-collapse:collapse; mso-table-lspace:0pt; mso-table-rspace:0pt; }\n")
        .append("img { border:0; height:auto; line-height:100%; outline:none; ")
        .append("text-decoration:none; -ms-interpolation-mode:bicubic; }\n")
        .append("a { text-decoration:none; }\n")
        .append(".email-page { background-color:#eef1f6; }\n")
        .append(".email-card, .email-header, .email-content, .email-help ")
        .append("{ background-color:#ffffff; }\n")
        .append(".email-footer { background-color:#f8fafc; }\n")
        .append(".email-title, .email-wordmark { color:#111827; }\n")
        .append(".email-subtitle, .email-muted, .email-label, .email-tagline ")
        .append("{ color:#5b6b82; }\n")
        .append(".email-body { color:#374151; }\n")
        .append(".email-notice { background-color:#f6f8fb; border-color:#dbe3ee; }\n")
        .append(".email-notice-title { color:#1f2937; }\n")
        .append(".email-notice-text { color:#46556b; }\n")
        .append(".email-divider, .email-help, .email-footer { border-color:#e4eaf2; }\n")
        .append(".email-link { color:#1d4ed8; }\n")
        .append(".email-os { color:#2563eb; }\n")
        .append(".email-eyebrow { color:#2563eb; }\n")
        .append(".email-mark { background-color:#2563eb; }\n")
        .append(".email-mark-text, .email-button { color:#ffffff; }\n")
        .append(".email-button-cell { background-color:#dc2626; }\n");

    css.append("@media (prefers-color-scheme: dark) {\n")
        .append(darkRules(""))
        .append("}\n");
    // Outlook.com applies these markers instead of supporting prefers-color-scheme.
    css.append(darkRules("[data-ogsc] "));
    css.append(darkRules("[data-ogsb] "));

    css.append("@media screen and (max-width:620px) {\n")
        .append(".email-shell { padding:16px 10px !important; }\n")
        .append(".email-header { padding:28px 22px 4px !important; }\n")
        .append(".email-content { padding:20px 22px 30px !important; }\n")
        .append(".email-help { padding:22px !important; }\n")
        .append(".email-footer { padding:22px !important; }\n")
        .append(".email-title { font-size:23px !important; }\n")
        .append(".email-subtitle { font-size:14px !important; }\n")
        .append(".email-button-table, .email-button-cell { width:100% !important; }\n")
        .append(".email-button { display:block !important; width:100% !important; ")
        .append("box-sizing:border-box !important; }\n")
        .append(".email-notice { border-radius:10px !important; }\n")
        .append("}\n</style>\n");
    return css.toString();
  }

  private static String darkRules(String scope) {
    StringBuilder css = new StringBuilder(2_000);
    dark(css, scope, ".email-page", "background-color:#0b0f17");
    dark(css, scope, ".email-card, " + scope + ".email-header, " + scope + ".email-content, "
        + scope + ".email-help", "background-color:#151b26");
    dark(css, scope, ".email-footer", "background-color:#111722");
    dark(css, scope, ".email-title, " + scope + ".email-wordmark", "color:#f8fafc");
    dark(css, scope, ".email-subtitle, " + scope + ".email-body", "color:#d7dee9");
    dark(css, scope, ".email-muted, " + scope + ".email-label, " + scope + ".email-tagline",
        "color:#a8b3c4");
    dark(css, scope, ".email-notice", "background-color:#1b2433;border-color:#334155");
    dark(css, scope, ".email-notice-title", "color:#f1f5f9");
    dark(css, scope, ".email-notice-text", "color:#cbd5e1");
    dark(css, scope, ".email-help, " + scope + ".email-footer", "border-color:#2a3444");
    dark(css, scope, ".email-link, " + scope + ".email-os, " + scope + ".email-eyebrow",
        "color:#93c5fd");
    dark(css, scope, ".email-mark", "background-color:#2563eb");
    dark(css, scope, ".email-logo", "background-color:#ffffff");
    return css.toString();
  }

  private static void dark(StringBuilder css, String scope, String selectors, String declarations) {
    css.append(scope).append(selectors).append(" { ").append(declarations)
        .append(" !important; }\n");
  }

  // ---------------------------------------------------------------------
  // Plain text
  // ---------------------------------------------------------------------

  private String text(EmailContent content) {
    StringBuilder text = new StringBuilder(1_500);
    text.append(content.title()).append("\n\n");
    if (!content.subtitle().isEmpty()) {
      text.append(content.subtitle()).append("\n\n");
    }
    if (!content.greeting().isEmpty()) {
      text.append(content.greeting()).append("\n\n");
    }
    for (String paragraph : content.paragraphs()) {
      text.append(paragraph).append("\n\n");
    }
    if (content.action() != null) {
      text.append(content.action().label()).append(":\n")
          .append(safeUrl(content.action().url())).append("\n\n");
      if (!content.action().hint().isEmpty()) {
        text.append(content.action().hint()).append("\n\n");
      }
    }
    if (content.notice() != null) {
      if (!content.notice().title().isEmpty()) {
        text.append(content.notice().title().toUpperCase(Locale.ROOT)).append("\n");
      }
      if (!content.notice().text().isEmpty()) {
        text.append(content.notice().text()).append("\n");
      }
      text.append("\n");
    }
    if (hasContact()) {
      text.append("Need help?\n");
      if (!brand.supportEmail().isEmpty()) {
        text.append("Email: ").append(brand.supportEmail()).append("\n");
      }
      if (!brand.supportPhone().isEmpty()) {
        text.append("Phone: ").append(brand.supportPhone()).append("\n");
      }
      if (!brand.supportHours().isEmpty()) {
        text.append("Hours: ").append(brand.supportHours()).append("\n");
      }
      if (!brand.helpUrl().isEmpty()) {
        text.append("Help centre: ").append(brand.helpUrl()).append("\n");
      }
      text.append("\n");
    }
    text.append("This is an automated message from ").append(brand.appName())
        .append(". Please do not reply.\n");
    if (!content.footerNote().isEmpty()) {
      text.append(content.footerNote()).append("\n");
    }
    text.append("\n\u00a9 ").append(Year.now().getValue()).append(" ")
        .append(brand.companyName()).append(". All rights reserved.\n");
    if (!brand.footerAddress().isEmpty()) {
      text.append(brand.footerAddress()).append("\n");
    }
    return text.toString();
  }

  private boolean hasContact() {
    return !brand.supportEmail().isEmpty() || !brand.supportPhone().isEmpty()
        || !brand.supportHours().isEmpty() || !brand.helpUrl().isEmpty();
  }

  // ---------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------

  private static String safeUrl(String value) {
    String url = value == null ? "" : value.trim();
    String lower = url.toLowerCase(Locale.ROOT);
    if (lower.startsWith("https://") || lower.startsWith("http://")
        || lower.startsWith("mailto:")) {
      return url;
    }
    return "#";
  }

  private static String telHref(String phone) {
    String cleaned = phone.replaceAll("[^0-9+]", "");
    return cleaned.isEmpty() ? "#" : "tel:" + cleaned;
  }

  private static String attr(String value) {
    return escape(value);
  }

  private static String escape(String value) {
    if (value == null) {
      return "";
    }
    StringBuilder escaped = new StringBuilder(value.length() + 16);
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      switch (character) {
        case '&' -> escaped.append("&amp;");
        case '<' -> escaped.append("&lt;");
        case '>' -> escaped.append("&gt;");
        case '"' -> escaped.append("&quot;");
        case '\'' -> escaped.append("&#39;");
        default -> escaped.append(character);
      }
    }
    return escaped.toString();
  }

  /** The HTML body and the plain-text alternative generated from one content model. */
  public record RenderedEmail(String html, String text) {
  }
}
