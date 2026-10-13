package com.smartline.smartos.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jsonwebtoken.Claims;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * JwtService must accept standard Base64, URL-safe Base64, and raw passphrase
 * secrets (regression test: JJWT's DecodingException does not extend
 * IllegalArgumentException, so catching only the latter killed bean creation
 * for any non-standard secret). Test-only keys; never use these in production.
 */
class JwtServiceTest {

  private static final String BASE64_SECRET =
      Base64.getEncoder().encodeToString(new byte[64]);
  private static final String BASE64URL_SECRET =
      Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[48]);
  private static final String PASSPHRASE_SECRET =
      "a-plain-passphrase-that-is-long-enough-12345";

  @Test
  void accessTokenRoundTrip() {
    JwtService service = new JwtService(BASE64_SECRET, 900000, 604800000);
    UUID userId = UUID.randomUUID();
    UUID orgId = UUID.randomUUID();

    String token = service.generateAccessToken(userId, orgId, List.of("ORG_ADMIN"));

    assertTrue(service.isValid(token));
    Claims claims = service.parse(token);
    assertEquals(userId.toString(), claims.getSubject());
    assertEquals(orgId, service.extractOrgId(claims));
    assertEquals("access", claims.get("type", String.class));
    assertTrue(claims.get("roles", List.class).contains("ORG_ADMIN"));
  }

  @Test
  void accessTokenIncludesEmailClaim() {
    JwtService service = new JwtService(BASE64_SECRET, 900000, 604800000);

    String token = service.generateAccessToken(UUID.randomUUID(), UUID.randomUUID(),
        List.of("ORG_ADMIN"), "Admin@Acme.Test");

    assertEquals("admin@acme.test", service.parse(token).get("email", String.class));
  }

  @Test
  void refreshTokenHasRefreshType() {
    JwtService service = new JwtService(BASE64_SECRET, 900000, 604800000);
    UUID orgId = UUID.randomUUID();

    String token = service.generateRefreshToken(UUID.randomUUID(), orgId);

    assertTrue(service.isValid(token));
    assertEquals("refresh", service.parse(token).get("type", String.class));
    assertEquals(orgId, service.extractOrgId(service.parse(token)));
  }

  @Test
  void tamperedAndGarbageTokensAreRejected() {
    JwtService service = new JwtService(BASE64_SECRET, 900000, 604800000);
    String token = service.generateAccessToken(UUID.randomUUID(), UUID.randomUUID(), List.of());

    assertFalse(service.isValid(token + "tampered"));
    assertFalse(service.isValid("not-a-token"));
  }

  @Test
  void urlSafeBase64SecretIsAccepted() {
    JwtService service = new JwtService(BASE64URL_SECRET, 900000, 604800000);
    UUID orgId = UUID.randomUUID();

    String token = service.generateAccessToken(UUID.randomUUID(), orgId, List.of("EDITOR"));

    assertTrue(service.isValid(token));
    assertEquals(orgId, service.extractOrgId(service.parse(token)));
  }

  @Test
  void plainPassphraseSecretIsAccepted() {
    JwtService service = new JwtService(PASSPHRASE_SECRET, 900000, 604800000);

    assertTrue(service.isValid(
        service.generateAccessToken(UUID.randomUUID(), UUID.randomUUID(), List.of())));
  }

  @Test
  void shortSecretFailsFastWithActionableMessage() {
    IllegalStateException ex = assertThrows(IllegalStateException.class,
        () -> new JwtService("too-short", 900000, 604800000));

    assertTrue(ex.getMessage().contains("JWT_SECRET"));
  }
}
