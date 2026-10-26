package com.smartline.smartos.modules.user;

import com.smartline.smartos.common.exception.ApiException;
import com.smartline.smartos.modules.organization.Organization;
import com.smartline.smartos.modules.organization.OrganizationRepository;
import com.smartline.smartos.security.AuthDtos;
import com.smartline.smartos.security.AuthDtos.LoginRequest;
import com.smartline.smartos.security.AuthDtos.RegisterOrgRequest;
import com.smartline.smartos.security.AuthDtos.SessionInfo;
import com.smartline.smartos.security.AuthDtos.TokenResponse;
import com.smartline.smartos.security.AuthDtos.TwoFactorVerifyRequest;
import com.smartline.smartos.security.AuthRateLimiter;
import com.smartline.smartos.security.JwtService;
import com.smartline.smartos.security.Permission;
import com.smartline.smartos.security.Role;
import com.smartline.smartos.security.RolePermissions;
import com.smartline.smartos.security.TokenHashes;
import com.smartline.smartos.security.UserPrincipal;
import io.jsonwebtoken.Claims;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
  private final AuthRateLimiter rateLimiter;

  /**
   * A real BCrypt hash used to equalise timing when the email is unknown.
   * Never a credential: the plaintext is not used anywhere.
   */
  private static final String DUMMY_BCRYPT_HASH =
      "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

  public AuthService(AppUserRepository users, OrganizationRepository organizations,
      RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder,
      JwtService jwtService, TwoFactorService twoFactor, AuthRateLimiter rateLimiter) {
    this.users = users;
    this.organizations = organizations;
    this.refreshTokens = refreshTokens;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.twoFactor = twoFactor;
    this.rateLimiter = rateLimiter;
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

  /** Used by internal callers/tests that do not have a request context. */
  @Transactional
  public TokenResponse login(LoginRequest req) {
    return login(req, "unknown");
  }

  @Transactional
  public TokenResponse login(LoginRequest req, String clientIp) {
    String email = normalizeEmail(req.getEmail());
    if (!rateLimiter.loginAllowed(email, clientIp)) {
      throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS",
          "Too many sign-in attempts. Try again later.");
    }

    List<AppUser> candidates;
    if (req.getOrganizationId() != null) {
      candidates = users.findByOrganization_IdAndEmailIgnoreCase(req.getOrganizationId(), email)
          .filter(AppUser::isActive)
          .map(List::of)
          .orElseGet(List::of);
    } else {
      // The same email may exist in several organizations (unique per org,
      // not globally). Resolve it by password rather than refusing outright:
      // with different passwords only one account can match, which is
      // unambiguous. If the same password opens more than one account we ask
      // for the organization explicitly.
      candidates = users.findByEmailIgnoreCase(email).stream()
          .filter(AppUser::isActive)
          .toList();
    }

    if (candidates.isEmpty()) {
      // Equalise timing with the real password-check path and keep the
      // response identical so account existence is not disclosed.
      passwordEncoder.matches(req.getPassword(), DUMMY_BCRYPT_HASH);
      throw loginFailed(email, clientIp);
    }

    List<AppUser> passwordMatches = candidates.stream()
        .filter(u -> passwordEncoder.matches(req.getPassword(), u.getPasswordHash()))
        .toList();

    AppUser user;
    if (passwordMatches.size() == 1) {
      user = passwordMatches.get(0);
    } else if (passwordMatches.size() > 1) {
      rateLimiter.recordLoginFailure(email, clientIp);
      throw new ApiException(HttpStatus.UNAUTHORIZED, "AMBIGUOUS_TENANT",
          "Email belongs to multiple organizations; specify organizationId");
    } else {
      throw loginFailed(email, clientIp);
    }

    rateLimiter.clearLoginFailures(email, clientIp);
    UUID orgId = user.getOrganization().getId();
    if (user.isTotpEnabled()) {
      // Second factor required: issue a short-lived challenge instead of
      // tokens. The challenge is verified (TOTP or backup code) to get tokens.
      return TokenResponse.challenge(jwtService.generateChallengeToken(user.getId(), orgId));
    }
    return issueTokens(user, orgId);
  }

  private ApiException loginFailed(String email, String clientIp) {
    rateLimiter.recordLoginFailure(email, clientIp);
    return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid credentials");
  }

  private static String normalizeEmail(String raw) {
    return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
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
    if (jwtService.extractAuthVersion(claims) != user.getAuthVersion()) {
      // Password changed/reset since this token was minted.
      revokeFamily(user.getId());
      throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid refresh token");
    }
    UUID orgId = jwtService.extractOrgId(claims);
    UUID effectiveOrg = orgId != null ? orgId : user.getOrganization().getId();
    List<String> roles = List.of(user.getRole().name());
    String access = jwtService.generateAccessToken(user.getId(), effectiveOrg, roles, user.getEmail(),
        user.getAuthVersion());
    String nextRefresh = storeRefreshWithRetry(user);
    stored.setRevoked(true);
    stored.setReplacedBy(null); // Will be set by the new token's ID after save
    refreshTokens.save(stored);
    return new TokenResponse(access, nextRefresh, "Bearer");
  }

  /**
   * The caller's effective access: live role, the permissions that role
   * carries, and the roles they may assign to others.
   *
   * <p>Read fresh from the database so a role change is reflected on the very
   * next request rather than after a token refresh.
   */
  @Transactional(readOnly = true)
  public AuthDtos.SessionInfo sessionInfo() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
    }
    AppUser user = users.findById(principal.getUserId())
        .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
            "Authentication required"));
    if (!user.isActive()) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
    }
    List<String> roles = List.of(user.getRole().name());
    List<String> permissions = RolePermissions.forRoleNames(roles).stream()
        .map(Permission::getName)
        .sorted()
        .toList();
    List<Role> assignable = user.getRole() == Role.PLATFORM_ADMIN
        ? List.of(Role.values())
        : RolePermissions.assignableByOrgAdmin();
    return new SessionInfo(user.getId(), user.getOrganizationId(), user.getEmail(),
        user.getFullName(), user.getRole(), roles, permissions, assignable);
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
    String access = jwtService.generateAccessToken(user.getId(), orgId, roles, user.getEmail(),
        user.getAuthVersion());
    String refresh = storeRefreshWithRetry(user);
    return new TokenResponse(access, refresh, "Bearer");
  }

  private String storeRefreshWithRetry(AppUser user) {
    int maxRetries = 3;
    for (int attempt = 0; attempt < maxRetries; attempt++) {
      String refreshToken = jwtService.generateRefreshToken(user.getId(),
          user.getOrganization().getId(), user.getAuthVersion());
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
