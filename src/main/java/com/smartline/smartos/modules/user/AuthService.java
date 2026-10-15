package com.smartline.smartos.modules.user;

import com.smartline.smartos.common.exception.ApiException;
import com.smartline.smartos.modules.organization.Organization;
import com.smartline.smartos.modules.organization.OrganizationRepository;
import com.smartline.smartos.security.AuthDtos.LoginRequest;
import com.smartline.smartos.security.AuthDtos.RegisterOrgRequest;
import com.smartline.smartos.security.AuthDtos.TokenResponse;
import com.smartline.smartos.security.AuthDtos.TwoFactorVerifyRequest;
import com.smartline.smartos.security.JwtService;
import com.smartline.smartos.security.Role;
import com.smartline.smartos.security.TokenHashes;
import io.jsonwebtoken.Claims;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  private final AppUserRepository users;
  private final OrganizationRepository organizations;
  private final RefreshTokenRepository refreshTokens;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final TwoFactorService twoFactor;

  public AuthService(AppUserRepository users, OrganizationRepository organizations,
      RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder,
      JwtService jwtService, TwoFactorService twoFactor) {
    this.users = users;
    this.organizations = organizations;
    this.refreshTokens = refreshTokens;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.twoFactor = twoFactor;
  }

  @Transactional
  public TokenResponse bootstrapOrganization(RegisterOrgRequest req) {
    String slug = req.getSlug().trim().toLowerCase();
    if (organizations.existsBySlug(slug)) {
      throw ApiException.conflict("Organization slug already exists: " + slug);
    }
    Organization org = new Organization();
    org.setName(req.getOrganizationName().trim());
    org.setSlug(slug);
    org.setPlan(Organization.Plan.FREE);
    Organization saved = organizations.save(org);

    AppUser admin = new AppUser();
    admin.setOrganization(saved);
    admin.setEmail(req.getAdminEmail().trim().toLowerCase());
    admin.setPasswordHash(passwordEncoder.encode(req.getAdminPassword()));
    admin.setFullName(req.getAdminFullName().trim());
    admin.setRole(Role.ORG_ADMIN);
    admin.setActive(true);
    AppUser savedAdmin = users.save(admin);

    return issueTokens(savedAdmin, saved.getId());
  }

  @Transactional
  public TokenResponse login(LoginRequest req) {
    String email = req.getEmail().trim().toLowerCase();
    AppUser user;
    if (req.getOrganizationId() != null) {
      user = users.findByOrganization_IdAndEmail(req.getOrganizationId(), email)
          .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid credentials"));
    } else {
      // Same email can exist in multiple organizations (unique per org, not
      // globally): never pick an organization arbitrarily.
      List<AppUser> matches = users.findByEmailIgnoreCase(email).stream()
          .filter(AppUser::isActive)
          .toList();
      if (matches.size() > 1) {
        throw new ApiException(HttpStatus.UNAUTHORIZED, "AMBIGUOUS_TENANT",
            "Email belongs to multiple organizations; specify organizationId");
      }
      user = matches.stream()
          .findFirst()
          .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid credentials"));
    }
    if (!user.isActive() || !passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid credentials");
    }
    UUID orgId = user.getOrganization().getId();
    if (user.isTotpEnabled()) {
      // Second factor required: issue a short-lived challenge instead of
      // tokens. The challenge is verified (TOTP or backup code) to get tokens.
      return TokenResponse.challenge(jwtService.generateChallengeToken(user.getId(), orgId));
    }
    return issueTokens(user, orgId);
  }

  /**
   * Completes a 2FA login challenge with a TOTP code or an unused backup
   * code (consumed on success).
   */
  @Transactional
  public TokenResponse verifyTwoFactor(TwoFactorVerifyRequest req) {
    Claims claims;
    try {
      claims = jwtService.parse(req.getChallengeToken().trim());
    } catch (Exception e) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid challenge");
    }
    if (!"2fa-challenge".equals(claims.get("type", String.class))) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid challenge");
    }
    UUID userId;
    try {
      userId = UUID.fromString(claims.getSubject());
    } catch (IllegalArgumentException e) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid challenge");
    }
    AppUser user = users.findById(userId)
        .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
            "Invalid credentials"));
    if (!user.isActive()) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid credentials");
    }
    if (!twoFactor.verifyChallenge(user, req.getCode())) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid credentials");
    }
    UUID orgId = jwtService.extractOrgId(claims);
    UUID effectiveOrg = orgId != null ? orgId : user.getOrganization().getId();
    return issueTokens(user, effectiveOrg);
  }

  /**
   * Rotating refresh: a presented token is revoked and replaced. Presenting an
   * already-rotated (or logged-out) token signals compromise, so the whole
   * family is revoked and the caller gets 401.
   */
  @Transactional
  public TokenResponse refresh(String refreshToken) {
    Claims claims;
    try {
      claims = jwtService.parse(refreshToken);
    } catch (Exception e) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid refresh token");
    }
    if (!"refresh".equals(claims.get("type", String.class))) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid refresh token");
    }
    RefreshToken stored = refreshTokens.findByTokenHash(TokenHashes.sha256Hex(refreshToken))
        .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
            "Invalid refresh token"));
    if (stored.isRevoked() || stored.getExpiresAt().isBefore(Instant.now())) {
      revokeFamily(stored.getUser().getId());
      throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid refresh token");
    }
    AppUser user = stored.getUser();
    if (user == null || !user.isActive()) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid refresh token");
    }
    UUID orgId = jwtService.extractOrgId(claims);
    UUID effectiveOrg = orgId != null ? orgId : user.getOrganization().getId();
    List<String> roles = List.of(user.getRole().name());
    String access = jwtService.generateAccessToken(user.getId(), effectiveOrg, roles, user.getEmail());
    String nextRefresh = storeRefreshWithRetry(user);
    stored.setRevoked(true);
    stored.setReplacedBy(null); // Will be set by the new token's ID after save
    refreshTokens.save(stored);
    return new TokenResponse(access, nextRefresh, "Bearer");
  }

  @Transactional
  public void logout(String refreshToken) {
    if (refreshToken == null || refreshToken.isBlank()) {
      return;
    }
    refreshTokens.findByTokenHash(TokenHashes.sha256Hex(refreshToken.trim()))
        .ifPresent(record -> {
          record.setRevoked(true);
          refreshTokens.save(record);
        });
  }

  private TokenResponse issueTokens(AppUser user, UUID orgId) {
    List<String> roles = List.of(user.getRole().name());
    String access = jwtService.generateAccessToken(user.getId(), orgId, roles, user.getEmail());
    String refresh = storeRefreshWithRetry(user);
    return new TokenResponse(access, refresh, "Bearer");
  }

  private String storeRefreshWithRetry(AppUser user) {
    int maxRetries = 3;
    for (int attempt = 0; attempt < maxRetries; attempt++) {
      String refreshToken = jwtService.generateRefreshToken(user.getId(), user.getOrganization().getId());
      String hash = TokenHashes.sha256Hex(refreshToken);
      RefreshToken record = new RefreshToken();
      record.setUser(user);
      record.setTokenHash(hash);
      record.setExpiresAt(Instant.now().plusMillis(jwtService.getRefreshExpiryMs()));
      try {
        // Flush inside the retry loop: without it the INSERT only executes
        // at commit time and a duplicate hash escapes as a 500 instead of
        // being retried here.
        refreshTokens.saveAndFlush(record);
        return refreshToken;
      } catch (DataIntegrityViolationException e) {
        if (attempt == 2) {
          throw new RuntimeException("Failed to generate unique refresh token after 3 attempts", e);
        }
        // Retry with a new token
      }
    }
    throw new IllegalStateException("Failed to generate unique refresh token after retries");
  }

  private void revokeFamily(UUID userId) {
    for (RefreshToken record : refreshTokens.findByUserId(userId)) {
      if (!record.isRevoked()) {
        record.setRevoked(true);
        refreshTokens.save(record);
      }
    }
  }
}
