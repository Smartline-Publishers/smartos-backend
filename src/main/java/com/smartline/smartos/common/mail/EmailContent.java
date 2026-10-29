package com.smartline.smartos.common.mail;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Content for one branded email. One immutable value is shared by every sender,
 * so the layout, dark-mode rules and footer only exist in
 * {@link BrandedEmailTemplate}.
 *
 * <p>Use the factory methods for the standard transactional emails, or the
 * builder for anything new:
 *
 * <pre>{@code
 * EmailContent.builder("Your export is ready")
 *     .greeting("Hello Alex,")
 *     .paragraph("The report you requested is attached.")
 *     .action("Open report", link)
 *     .infoNotice("Expires soon", "Download it within 7 days.")
 *     .build();
 * }</pre>
 */
public final class EmailContent {

  private final String preheader;
  private final String eyebrow;
  private final String title;
  private final String subtitle;
  private final String greeting;
  private final List<String> paragraphs;
  private final Action action;
  private final Notice notice;
  private final String footerNote;

  private EmailContent(Builder builder) {
    this.preheader = clean(builder.preheader);
    this.eyebrow = clean(builder.eyebrow);
    this.title = clean(builder.title);
    this.subtitle = clean(builder.subtitle);
    this.greeting = clean(builder.greeting);
    this.paragraphs = builder.paragraphs.stream().map(EmailContent::clean)
        .filter(value -> !value.isEmpty()).toList();
    this.action = builder.action;
    this.notice = builder.notice;
    this.footerNote = clean(builder.footerNote);
    if (this.title.isEmpty()) {
      throw new IllegalArgumentException("Email title is required");
    }
  }

  public static Builder builder(String title) {
    return new Builder().title(title);
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Standard password-reset message used by {@code PasswordResetMailer}. */
  public static EmailContent passwordReset(
      String appName, String recipientName, String link, long ttlMinutes) {
    String expires = duration(ttlMinutes, "30 minutes");
    return builder("Reset your password")
        .preheader("Choose a new password for your " + appName + " account.")
        .eyebrow("ACCOUNT SECURITY")
        .subtitle("We received a request to reset the password for your "
            + appName + " account.")
        .greeting("Hello " + nameOrThere(recipientName) + ",")
        .paragraph("Use the button below to choose a new password. "
            + "This single-use link expires in " + expires + ".")
        .paragraph("If you did not request this, you can safely ignore this email. "
            + "Your password will not change.")
        .action("Reset password", link, "This link can only be used once.")
        .securityNotice("Security notice", "For your security, this link expires in "
            + expires + " and can only be used once. Never share this email with anyone.")
        .footerNote("You received this email because a password reset was requested "
            + "for your account.")
        .build();
  }

  /** Template for an email-address verification message. */
  public static EmailContent verifyEmail(
      String appName, String recipientName, String link, long ttlHours) {
    String expires = duration(ttlHours * 60, "24 hours");
    return builder("Verify your email address")
        .preheader("Confirm your email address to finish setting up your "
            + appName + " account.")
        .eyebrow("ACCOUNT VERIFICATION")
        .subtitle("Thanks for joining " + appName + ".")
        .greeting("Hello " + nameOrThere(recipientName) + ",")
        .paragraph("Confirm this email address to finish setting up your account and "
            + "start using " + appName + ".")
        .paragraph("If you did not create an account, you can safely ignore this email.")
        .action("Verify email address", link, "This link can only be used once.")
        .securityNotice("Security notice", "This verification link expires in "
            + expires + ". Please do not share this email with anyone.")
        .footerNote("You received this email because this address was used to create "
            + "a " + appName + " account.")
        .build();
  }

  /** Template for the first message after an account is activated. */
  public static EmailContent welcome(String appName, String recipientName, String link) {
    return builder("Welcome to " + appName)
        .preheader("Your " + appName + " workspace is ready.")
        .eyebrow("WELCOME")
        .subtitle("Your workspace is ready.")
        .greeting("Hello " + nameOrThere(recipientName) + ",")
        .paragraph("Your account is set up. Open " + appName + " to add your team, "
            + "organise your work and pick up where you left off.")
        .action("Open " + appName, link)
        .infoNotice("Getting started",
            "Visit the Help Centre from the account menu whenever you need a hand.")
        .footerNote("You received this email because a " + appName
            + " account was created for this address.")
        .build();
  }

  /** Template for system and activity notifications. */
  public static EmailContent notification(
      String appName, String recipientName, String title, String message, String link) {
    Builder builder = builder(title)
        .preheader(message)
        .eyebrow("NOTIFICATION")
        .subtitle("A quick update from " + appName + ".")
        .greeting("Hello " + nameOrThere(recipientName) + ",")
        .paragraph(message)
        .footerNote("You received this email because of your notification settings "
            + "for " + appName + ".");
    if (link != null && !link.isBlank()) {
      builder.action("View details", link);
    }
    return builder.build();
  }

  public String preheader() {
    return preheader;
  }

  public String eyebrow() {
    return eyebrow;
  }

  public String title() {
    return title;
  }

  public String subtitle() {
    return subtitle;
  }

  public String greeting() {
    return greeting;
  }

  public List<String> paragraphs() {
    return paragraphs;
  }

  public Action action() {
    return action;
  }

  public Notice notice() {
    return notice;
  }

  public String footerNote() {
    return footerNote;
  }

  private static String nameOrThere(String name) {
    return name == null || name.isBlank() ? "there" : name.trim();
  }

  private static String duration(long minutes, String fallback) {
    if (minutes <= 0) {
      return fallback;
    }
    if (minutes % 60 == 0) {
      long hours = minutes / 60;
      return hours + (hours == 1 ? " hour" : " hours");
    }
    return minutes + (minutes == 1 ? " minute" : " minutes");
  }

  private static String clean(String value) {
    return value == null ? "" : value.trim();
  }

  /** Optional call to action. {@code hint} is shown under the button. */
  public record Action(String label, String url, String hint) {
    public Action {
      label = clean(label);
      url = clean(url);
      hint = clean(hint);
      if (label.isEmpty() || url.isEmpty()) {
        throw new IllegalArgumentException("Action label and URL are required");
      }
    }
  }

  /** Optional callout box. */
  public record Notice(String title, String text, NoticeTone tone) {
    public Notice {
      Objects.requireNonNull(tone, "tone");
      title = clean(title);
      text = clean(text);
    }
  }

  public enum NoticeTone {
    INFO, SECURITY, WARNING
  }

  public static final class Builder {

    private String preheader;
    private String eyebrow;
    private String title;
    private String subtitle;
    private String greeting;
    private final List<String> paragraphs = new ArrayList<>();
    private Action action;
    private Notice notice;
    private String footerNote;

    private Builder() {
    }

    public Builder preheader(String value) {
      this.preheader = value;
      return this;
    }

    public Builder eyebrow(String value) {
      this.eyebrow = value;
      return this;
    }

    public Builder title(String value) {
      this.title = value;
      return this;
    }

    public Builder subtitle(String value) {
      this.subtitle = value;
      return this;
    }

    public Builder greeting(String value) {
      this.greeting = value;
      return this;
    }

    public Builder paragraph(String value) {
      if (value != null && !value.isBlank()) {
        this.paragraphs.add(value);
      }
      return this;
    }

    public Builder paragraphs(List<String> values) {
      if (values != null) {
        values.forEach(this::paragraph);
      }
      return this;
    }

    public Builder action(String label, String url) {
      this.action = new Action(label, url, "");
      return this;
    }

    public Builder action(String label, String url, String hint) {
      this.action = new Action(label, url, hint);
      return this;
    }

    public Builder notice(Notice value) {
      this.notice = value;
      return this;
    }

    public Builder infoNotice(String title, String text) {
      return notice(new Notice(title, text, NoticeTone.INFO));
    }

    public Builder securityNotice(String title, String text) {
      return notice(new Notice(title, text, NoticeTone.SECURITY));
    }

    public Builder warningNotice(String title, String text) {
      return notice(new Notice(title, text, NoticeTone.WARNING));
    }

    public Builder footerNote(String value) {
      this.footerNote = value;
      return this;
    }

    public EmailContent build() {
      return new EmailContent(this);
    }
  }
}
