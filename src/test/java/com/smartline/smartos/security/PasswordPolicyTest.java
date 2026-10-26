package com.smartline.smartos.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.smartline.smartos.common.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class PasswordPolicyTest {

  @Test
  void acceptsLongMixedPasswords() {
    assertNull(PasswordPolicy.validate("Publ1shing-Desk!", "trace@test.local"));
    assertNull(PasswordPolicy.validate("a-very-long-passphrase-42", null));
  }

  @Test
  void rejectsMissingAndShortPasswords() {
    assertEquals("Password is required", PasswordPolicy.validate(null, null));
    assertEquals("Password is required", PasswordPolicy.validate("", null));
    assertEquals("Password must be at least 10 characters", PasswordPolicy.validate("Ab1!short", null));
  }

  @Test
  void rejectsPasswordsLongerThanBcryptCanHash() {
    String tooLong = "A1!" + "x".repeat(PasswordPolicy.MAX_BYTES);
    assertNotNull(PasswordPolicy.validate(tooLong, null));
  }

  @Test
  void rejectsCommonAndPredictablePasswords() {
    assertNotNull(PasswordPolicy.validate("password123", null));
    assertNotNull(PasswordPolicy.validate("1234567890123", null));
    assertNotNull(PasswordPolicy.validate("aaaaaaaaaa1", null));
    assertNotNull(PasswordPolicy.validate("abcdefghij", null));
    assertNotNull(PasswordPolicy.validate("zyxwvutsrq", null));
  }

  @Test
  void rejectsPasswordContainingTheEmailLocalPart() {
    String error = PasswordPolicy.validate("trace-is-my-password-9", "trace@test.local");
    assertEquals("Password must not contain your email address", error);
  }

  @Test
  void ignoresShortEmailLocalParts() {
    // "ab@x.test" only has a two-character local part, too weak a signal to
    // enforce and can false-positive on ordinary words.
    assertNull(PasswordPolicy.validate("about-the-ab-test-9", "ab@x.test"));
  }

  @Test
  void requiresLettersAndANumberOrSymbol() {
    assertNotNull(PasswordPolicy.validate("onlylettershere", null));
    assertNull(PasswordPolicy.validate("1234567890ab", null));
    assertNotNull(PasswordPolicy.validate("1234567890123", null));
  }

  @Test
  void requireValidThrowsBadRequest() {
    ApiException ex = assertThrows(ApiException.class,
        () -> PasswordPolicy.requireValid("short", "trace@test.local"));
    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    assertEquals("Password must be at least 10 characters", ex.getMessage());
  }
}
