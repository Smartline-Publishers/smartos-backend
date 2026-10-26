package com.smartline.smartos.security;

import java.util.Locale;
import java.util.Set;

import com.smartline.smartos.common.exception.ApiException;

/**
 * Password strength rules shared by registration, change-password and reset.
 *
 * <p>Rules are intentionally pragmatic rather than arbitrary composition
 * requirements (NIST SP 800-63B): length is the primary control, extremely
 * common/breached values and trivial patterns are rejected, and the password
 * is never trimmed or silently truncated.
 */
public final class PasswordPolicy {

  public static final int MIN_LENGTH = 10;
  /** BCrypt only considers the first 72 bytes. */
  public static final int MAX_BYTES = 72;

  private static final Set<String> COMMON_PASSWORDS = Set.of(
      "password", "password1", "password123", "passw0rd", "1234567890",
      "123456789", "12345678", "qwerty123", "qwertyuiop", "letmein123",
      "welcome123", "admin123", "administrator", "iloveyou", "abc123456",
      "changeme", "changeme123", "smartos123", "smartline123");

  private PasswordPolicy() {
  }

  public static String validate(String password, String email) {
    if (password == null || password.isEmpty()) {
      return "Password is required";
    }
    if (password.length() < MIN_LENGTH) {
      return "Password must be at least " + MIN_LENGTH + " characters";
    }
    if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BYTES) {
      return "Password is too long (maximum " + MAX_BYTES + " bytes)";
    }
    String normalized = password.toLowerCase(Locale.ROOT);
    if (COMMON_PASSWORDS.contains(normalized)) {
      return "That password is too common; choose a less predictable one";
    }
    if (normalized.chars().allMatch(Character::isDigit)) {
      return "Password cannot be only digits";
    }
    if (normalized.chars().distinct().count() <= 2) {
      return "Password is too repetitive";
    }
    // Reject simple runs such as 1234567890 or abcdefghij.
    if (normalized.length() >= MIN_LENGTH && isSequential(normalized)) {
      return "Password cannot be a simple sequence";
    }
    if (email != null && !email.isBlank()) {
      String localPart = email.trim().toLowerCase(Locale.ROOT);
      int at = localPart.indexOf('@');
      if (at > 0) {
        localPart = localPart.substring(0, at);
      }
      if (localPart.length() >= 4 && normalized.contains(localPart)) {
        return "Password must not contain your email address";
      }
    }
    boolean hasLetter = normalized.chars().anyMatch(Character::isLetter);
    boolean hasDigitOrSymbol = normalized.chars().anyMatch(c -> Character.isDigit(c) || !Character.isLetterOrDigit(c));
    if (!hasLetter || !hasDigitOrSymbol) {
      return "Password must include letters and at least one number or symbol";
    }
    return null;
  }

  public static void requireValid(String password, String email) {
    String error = validate(password, email);
    if (error != null) {
      throw ApiException.badRequest(error);
    }
  }

  private static boolean isSequential(String value) {
    int ascending = 0;
    int descending = 0;
    for (int i = 1; i < value.length(); i++) {
      int delta = value.charAt(i) - value.charAt(i - 1);
      if (delta == 1) {
        ascending++;
      } else if (delta == -1) {
        descending++;
      }
    }
    int run = Math.max(ascending, descending);
    return run >= value.length() - 1;
  }
}
