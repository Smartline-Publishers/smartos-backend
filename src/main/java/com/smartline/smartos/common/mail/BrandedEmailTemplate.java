package com.smartline.smartos.common.mail;

import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Renders the shared SmartOS email shell and a plain-text alternative.
 */
@Component
public class BrandedEmailTemplate {

  public RenderedEmail render(EmailContent content) {
    Objects.requireNonNull(content, "content");
    StringBuilder html = new StringBuilder();
    html.append("""
        <!doctype html>
        <html lang="en">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <meta name="color-scheme" content="light dark">
          <meta name="supported-color-schemes" content="light dark">
          <title>%s</title>
          <style>
            :root { color-scheme: light dark; supported-color-schemes: light dark; }
            body { margin: 0 !important; padding: 0 !important; background: #f2f4f8; }
            @media screen and (max-width: 600px) {
              .email-card { width: 100%% !important; border-radius: 0 !important; }
              .email-content { padding: 28px 22px !important; }
              .email-footer { padding: 22px !important; }
            }
            @media (prefers-color-scheme: dark) {
              .email-page { background: #111827 !important; }
              .email-card, .email-main { background: #1f2937 !important; }
              .email-title, .email-body, .email-footer { color: #f3f4f6 !important; }
              .email-muted { color: #cbd5e1 !important; }
              .email-notice { background: #273449 !important; border-color: #3b4b63 !important; }
              .email-divider { border-color: #374151 !important; }
              .email-link { color: #93c5fd !important; }
              .wordmark-smart { color: #ffffff !important; }
            }
            [data-ogsc] .email-page { background: #111827 !important; }
            [data-ogsc] .email-card, [data-ogsc] .email-main { background: #1f2937 !important; }
            [data-ogsc] .email-title, [data-ogsc] .email-body,
            [data-ogsc] .email-footer { color: #f3f4f6 !important; }
            [data-ogsc] .email-muted { color: #cbd5e1 !important; }
            [data-ogsc] .email-notice { background: #273449 !important; border-color: #3b4b63 !important; }
            [data-ogsc] .email-divider { border-color: #374151 !important; }
            [data-ogsc] .email-link { color: #93c5fd !important; }
            [data-ogsc] .wordmark-smart { color: #ffffff !important; }
          </style>
        </head>
        <body class="email-page" style="margin:0;padding:0;background-color:#f2f4f8;">
          <div style="display:none;font-size:1px;color:#f2f4f8;line-height:1px;max-height:0;
                      max-width:0;opacity:0;overflow:hidden;">%s</div>
          <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0"
                 class="email-page" style="background-color:#f2f4f8;">
            <tr><td align="center" style="padding:36px 16px;">
              <table role="presentation" width="600" cellspacing="0" cellpadding="0" border="0"
                     class="email-card" style="width:100%%;max-width:600px;background-color:#ffffff;
                     border-radius:12px;overflow:hidden;">
                <tr><td align="center" class="email-main"
                        style="padding:34px 40px 12px;background-color:#ffffff;">
                  <div style="font-family:Arial,Helvetica,sans-serif;font-size:25px;font-weight:700;
                              letter-spacing:-0.5px;">
                    <span class="wordmark-smart" style="color:#111827;">Smart</span><span
                      class="wordmark-os" style="color:#2563eb;">OS</span>
                  </div>
                  <div style="margin-top:7px;font-family:Arial,Helvetica,sans-serif;font-size:11px;
                              letter-spacing:1.5px;text-transform:uppercase;color:#64748b;">
                    SMARTLINE PUBLISHERS
                  </div>
                </td></tr>
                <tr><td class="email-content email-main"
                        style="padding:24px 40px 38px;background-color:#ffffff;
                        font-family:Arial,Helvetica,sans-serif;color:#1f2937;">
                  <h1 class="email-title" style="margin:0 0 12px;text-align:center;font-size:24px;
                      line-height:1.3;color:#111827;">%s</h1>
                  <p class="email-body" style="margin:0 0 20px;font-size:15px;line-height:1.65;
                      color:#374151;">%s</p>
        """.formatted(escape(content.title()), escape(content.preheader()), escape(content.title()),
        escape(content.greeting())));

    for (String paragraph : content.paragraphs()) {
      html.append("<p class=\"email-body\" style=\"margin:0 0 16px;font-size:15px;line-height:1.65;")
          .append("color:#374151;\">").append(escape(paragraph)).append("</p>\n");
    }

    if (hasText(content.actionLabel()) && hasText(content.actionUrl())) {
      html.append("""
                  <table role="presentation" cellspacing="0" cellpadding="0" border="0"
                         align="center" style="margin:26px auto 16px;">
                    <tr><td align="center" bgcolor="#dc2626" style="border-radius:7px;
                        background-color:#dc2626;">
                      <a href="%s" style="display:inline-block;padding:13px 24px;
                         font-family:Arial,Helvetica,sans-serif;font-size:15px;font-weight:700;
                         line-height:1.2;color:#ffffff;text-decoration:none;border-radius:7px;">
                        %s
                      </a>
                    </td></tr>
                  </table>
                  <p class="email-muted" style="margin:0 0 8px;text-align:center;font-size:13px;
                      line-height:1.5;color:#64748b;">If the button does not work, copy and paste
                      this link into your browser:</p>
                  <p style="margin:0 0 24px;text-align:center;font-size:13px;line-height:1.5;
                      overflow-wrap:anywhere;">
                    <a class="email-link" href="%s" style="color:#1d4ed8;word-break:break-all;">%s</a>
                  </p>
            """.formatted(escapeAttribute(content.actionUrl()), escape(content.actionLabel()),
        escapeAttribute(content.actionUrl()), escape(content.actionUrl())));
    }

    if (hasText(content.noticeText())) {
      html.append("""
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0"
                         border="0" class="email-notice"
                         style="background-color:#f8fafc;border:1px solid #e2e8f0;border-radius:6px;">
                    <tr><td style="padding:14px 16px;">
                      <strong class="email-body" style="display:block;margin-bottom:5px;
                        font-family:Arial,Helvetica,sans-serif;font-size:13px;color:#1f2937;">
                        %s
                      </strong>
                      <span class="email-muted" style="font-family:Arial,Helvetica,sans-serif;
                        font-size:13px;line-height:1.5;color:#475569;">%s</span>
                    </td></tr>
                  </table>
            """.formatted(escape(content.noticeTitle()), escape(content.noticeText())));
    }

    html.append("""
                </td></tr>
                <tr><td class="email-footer"
                        style="padding:24px 40px;background-color:#f8fafc;text-align:center;
                        font-family:Arial,Helvetica,sans-serif;font-size:12px;line-height:1.7;
                        color:#64748b;">
                  <div class="email-footer">This is an automated message from SmartOS.</div>
                  <div class="email-muted" style="margin-top:5px;color:#64748b;">
                    &copy; %d Smartline Publishers Ltd.
                  </div>
                </td></tr>
              </table>
            </td></tr>
          </table>
        </body>
        </html>
        """.formatted(java.time.Year.now().getValue()));

    StringBuilder text = new StringBuilder();
    text.append(content.title()).append("\n\n")
        .append(content.greeting()).append("\n\n");
    for (String paragraph : content.paragraphs()) {
      text.append(paragraph).append("\n\n");
    }
    if (hasText(content.actionLabel()) && hasText(content.actionUrl())) {
      text.append(content.actionLabel()).append(": ").append(content.actionUrl()).append("\n\n");
    }
    if (hasText(content.noticeText())) {
      if (hasText(content.noticeTitle())) {
        text.append(content.noticeTitle()).append(": ");
      }
      text.append(content.noticeText()).append("\n\n");
    }
    text.append("This is an automated message from SmartOS.\n");

    return new RenderedEmail(html.toString(), text.toString());
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private static String escape(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }

  private static String escapeAttribute(String value) {
    return escape(value);
  }

  public record EmailContent(
      String preheader,
      String title,
      String greeting,
      List<String> paragraphs,
      String actionLabel,
      String actionUrl,
      String noticeTitle,
      String noticeText) {
    public EmailContent {
      Objects.requireNonNull(title, "title");
      Objects.requireNonNull(greeting, "greeting");
      paragraphs = paragraphs == null ? List.of() : List.copyOf(paragraphs);
    }
  }

  public record RenderedEmail(String html, String text) {
  }
}
