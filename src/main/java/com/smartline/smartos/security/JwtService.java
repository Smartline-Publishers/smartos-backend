package com.smartline.smartos.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
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
    byte[] keyBytes;
    try {
      keyBytes = Decoders.BASE64.decode(secret);
    } catch (IllegalArgumentException e) {
      keyBytes = secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
    this.key = Keys.hmacShaKeyFor(keyBytes);
    this.accessExpiryMs = accessExpiryMs;
    this.refreshExpiryMs = refreshExpiryMs;
  }

  public String generateAccessToken(UUID userId, UUID orgId, Collection<String> roles) {
    return buildToken(userId, orgId, roles, accessExpiryMs, "access");
  }

  public String generateRefreshToken(UUID userId, UUID orgId) {
    return buildToken(userId, orgId, List.of(), refreshExpiryMs, "refresh");
  }

  private String buildToken(UUID userId, UUID orgId, Collection<String> roles, long ttlMs, String type) {
    long now = System.currentTimeMillis();
    var builder = Jwts.builder()
        .subject(userId.toString())
        .issuedAt(new Date(now))
        .expiration(new Date(now + ttlMs))
        .claim("type", type);
    if (orgId != null) {
      builder.claim("orgId", orgId.toString());
    }
    if (roles != null && !roles.isEmpty()) {
      builder.claim("roles", roles);
    }
    return builder.signWith(key).compact();
  }

  public Claims parse(String token) {
    return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
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
}
