package com.smartline.smartos.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class TotpServiceTest {

  private final TotpService totp = new TotpService();

  @Test
  void matchesRfc6238Vector() {
    // RFC 6238 Appendix B, SHA-1: secret "12345678901234567890" at T=59s
    // yields 94287082 (8 digits), i.e. 287082 truncated to 6 digits.
    String secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    assertTrue(totp.verify(secret, "287082", Instant.ofEpochSecond(59)));
    assertTrue(totp.verify(secret, "287082", Instant.ofEpochSecond(70)));
    assertFalse(totp.verify(secret, "287082", Instant.ofEpochSecond(120)));
  }

  @Test
  void generatedSecretsVerifyAndDiffer() {
    String first = totp.generateSecret();
    String second = totp.generateSecret();

    assertNotEquals(first, second);
    assertTrue(totp.verify(first, totp.codeAt(first, Instant.now())));
    assertFalse(totp.verify(first, totp.codeAt(second, Instant.now())));
    assertFalse(totp.verify(first, "000000"));
  }

  @Test
  void rejectsMalformedInput() {
    String secret = totp.generateSecret();

    assertFalse(totp.verify(secret, null));
    assertFalse(totp.verify(null, "123456"));
    assertFalse(totp.verify(secret, "abcdef"));
    assertFalse(totp.verify(secret, "12345"));
    assertFalse(totp.verify("not!base32!!", "123456"));
  }

  @Test
  void otpauthUrlHasRequiredParameters() {
    String url = totp.otpauthUrl("SmartOS", "admin@acme.test", "JBSWY3DPEHPK3PXP");

    assertTrue(url.startsWith("otpauth://totp/"));
    assertTrue(url.contains("secret=JBSWY3DPEHPK3PXP"));
    assertTrue(url.contains("issuer=SmartOS"));
    assertTrue(url.contains("period=30"));
  }
}
