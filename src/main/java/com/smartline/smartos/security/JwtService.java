package com.smartline.smartos.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  private final SecretKey key;
  private final long accessExpiryMs;
  private final long refreshExpiryMs;

  public JwtService(
      @Value("${app.jwt.secret:change-me-change-me-change-me-32bytes-min}") String secret,
      @Value("${app.jwt.access-expiry-ms:900000}") long accessExpiryMs,
      @Value("${app.jwt.refresh-expiry-ms:604800000}") long refreshExpiryMs) {
    byte[] keyBytes = decodeSecret(secret);
    if (keyBytes.length < 32) {
      throw new IllegalStateException(
          "app.jwt.secret (JWT_SECRET) must decode to at least 32 bytes (256 bits) for HMAC signing; "
              + "generate one with e.g. `openssl rand -base64 48` and set it via the JWT_SECRET environment variable");
    }
    this.key = Keys.hmacShaKeyFor(keyBytes);
    this.accessExpiryMs = accessExpiryMs;
    this.refreshExpiryMs = refreshExpiryMs;
  }

  /**
   * Accepts standard Base64 (e.g. {@code openssl rand -base64}), URL-safe Base64
   * (alphabet with {@code -} and {@code _}), or a raw passphrase.
   * JJWT wraps strict-Base64 failures in {@link DecodingException}, which does
   * <em>not</em> extend {@link IllegalArgumentException} — catching only the
   * latter lets a non-standard secret escape and kill bean initialization.
   */
  private static byte[] decodeSecret(String secret) {
    try {
      return Decoders.BASE64.decode(secret);
    } catch (DecodingException | IllegalArgumentException e) {
      // Not standard Base64: try the URL-safe alphabet next.
    }
    try {
      return Decoders.BASE64URL.decode(secret);
    } catch (DecodingException | IllegalArgumentException e) {
      // Not encoded at all: use raw bytes (strength is checked by the caller).
    }
    return secret.getBytes(StandardCharsets.UTF_8);
  }

  public String generateAccessToken(UUID userId, UUID orgId, Collection<String> roles) {
    return buildToken(userId, orgId, roles, null, accessExpiryMs, "access", 0);
  }

  public String generateAccessToken(UUID userId, UUID orgId, Collection<String> roles, String email) {
    return generateAccessToken(userId, orgId, roles, email, 0);
  }

  /**
   * Access token carrying the caller's {@code auth_version}. A password
   * change/reset bumps the version, which makes every older token stale.
   */
  public String generateAccessToken(UUID userId, UUID orgId, Collection<String> roles, String email,
      int authVersion) {
    return buildToken(userId, orgId, roles, email, accessExpiryMs, "access", authVersion);
  }

  public String generateRefreshToken(UUID userId, UUID orgId) {
    return generateRefreshToken(userId, orgId, 0);
  }

  public String generateRefreshToken(UUID userId, UUID orgId, int authVersion) {
    return buildToken(userId, orgId, List.of(), null, refreshExpiryMs, "refresh", authVersion);
  }

  /**
   * Short-lived second-factor challenge (5 minutes). Accepted only by the 2FA
   * verify endpoint — never as an access or refresh token.
   */
  public String generateChallengeToken(UUID userId, UUID orgId) {
    return buildToken(userId, orgId, List.of(), null, 300_000L, "2fa-challenge", 0);
  }

  private String buildToken(UUID userId, UUID orgId, Collection<String> roles, String email, long ttlMs,
      String type, int authVersion) {
    long now = System.currentTimeMillis();
    var builder = Jwts.builder()
        .subject(userId.toString())
        // Unique token id: JWT timestamps have one-second resolution, so two
        // tokens minted in the same second would otherwise be byte-identical.
        // Identical refresh tokens share a SHA-256 hash and collide on the
        // refresh_tokens.token_hash unique constraint (intermittent 500s).
        .id(UUID.randomUUID().toString())
        .issuedAt(new Date(now))
        .expiration(new Date(now + ttlMs))
        .claim("type", type)
        .claim("ver", authVersion);
    if (orgId != null) {
      builder.claim("orgId", orgId.toString());
    }
    if (email != null && !email.isBlank()) {
      builder.claim("email", email.trim().toLowerCase());
    }
    if (roles != null && !roles.isEmpty()) {
      builder.claim("roles", roles);
    }
    return builder.signWith(key).compact();
  }

  public Claims parse(String token) {
    return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
  }

  public long getAccessExpiryMs() {
    return accessExpiryMs;
  }

  public long getRefreshExpiryMs() {
    return refreshExpiryMs;
  }

  public boolean isValid(String token) {
    try {
      parse(token);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  public UUID extractOrgId(Claims claims) {
    String orgId = claims.get("orgId", String.class);
    return orgId != null ? UUID.fromString(orgId) : null;
  }

  /** Token generation, defaulting old tokens without the claim to version 0. */
  public int extractAuthVersion(Claims claims) {
    Integer version = claims.get("ver", Integer.class);
    return version != null ? version : 0;
  }
}
