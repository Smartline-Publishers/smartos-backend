package com.smartline.smartos.modules.user;

import com.smartline.smartos.common.exception.ApiException;
import com.smartline.smartos.common.exception.ErrorCode;
import com.smartline.smartos.modules.user.PasswordResetDtos.ForgotResponse;
import com.smartline.smartos.security.AuthRateLimiter;
import com.smartline.smartos.security.PasswordPolicy;
import com.smartline.smartos.security.TokenHashes;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Forgot-password / reset-password flow.
 *
 * <p>Security properties:
 * <ul>
 *   <li>256-bit random, single-use tokens; only SHA-256 hashes are stored.</li>
 *   <li>30-minute default expiry and row-level locking on redemption.</li>
 *   <li>No account-existence disclosure: every syntactically valid request
 *       gets the same response, and unknown emails take the same path.</li>
 *   <li>On success every refresh token is revoked and auth_version is bumped,
 *       so other devices are logged out immediately.</li>
 * </ul>
 */
@Service
public class PasswordResetService {

  private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final int MAX_ACCOUNTS_PER_EMAIL = 5;

  private final AppUserRepository users;
  private final PasswordResetTokenRepository resetTokens;
  private final RefreshTokenRepository refreshTokens;
  private final PasswordEncoder passwordEncoder;
  private final PasswordResetMailer mailer;
  private final AuthRateLimiter rateLimiter;
  private final long tokenTtlMinutes;
  private final boolean exposeDevToken;

  public PasswordResetService(AppUserRepository users, PasswordResetTokenRepository resetTokens,
      RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder,
      PasswordResetMailer mailer, AuthRateLimiter rateLimiter,
      @Value("${app.security.password-reset.token-ttl-minutes:30}") long tokenTtlMinutes,
      @Value("${app.security.password-reset.expose-dev-token:false}") boolean exposeDevToken) {
    this.users = users;
    this.resetTokens = resetTokens;
    this.refreshTokens = refreshTokens;
    this.passwordEncoder = passwordEncoder;
    this.mailer = mailer;
    this.rateLimiter = rateLimiter;
    this.tokenTtlMinutes = tokenTtlMinutes;
    this.exposeDevToken = exposeDevToken;
  }

  @Transactional
  public ForgotResponse requestReset(String rawEmail, String clientIp) {
    String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase(Locale.ROOT);

    // Account-independent check: if the transport is unusable, no request can
    // ever deliver a link. Failing fast with 503 is honest and leaks nothing
    // about which addresses exist. The dev-token mode needs no transport.
    if (!exposeDevToken && !mailer.isEnabled()) {
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.NOT_CONFIGURED.getCode(),
          "Password reset email is not available right now. "
              + "Please try again later or contact support.");
    }

    if (!rateLimiter.forgotAllowed(email, clientIp)) {
      throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS",
          "Too many reset requests. Try again later.");
    }

    String devToken = null;
    if (!email.isBlank()) {
      List<AppUser> accounts = users.findByEmailIgnoreCase(email).stream()
          .filter(AppUser::isActive)
          .limit(MAX_ACCOUNTS_PER_EMAIL)
          .toList();
      Instant now = Instant.now();
      Instant expiresAt = now.plus(tokenTtlMinutes, ChronoUnit.MINUTES);
      for (AppUser user : accounts) {
        // A new request invalidates any outstanding link for that account.
        resetTokens.invalidateAllForUser(user.getId(), now);

        String rawToken = newToken();
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(TokenHashes.sha256Hex(rawToken));
        token.setExpiresAt(expiresAt);
        token.setRequestedIp(clientIp);
        resetTokens.save(token);

        boolean delivered = mailer.send(user, rawToken);
        if (exposeDevToken && devToken == null) {
          devToken = rawToken;
        }
        if (!delivered && !exposeDevToken) {
          // Keep the public response generic to avoid exposing account existence.
          // The mailer logs the specific, secret-free configuration or transport failure.
          resetTokens.invalidateAllForUser(user.getId(), Instant.now());
        }
      }
    }
    return new ForgotResponse(true, devToken);
  }

  @Transactional(readOnly = true)
  public void validate(String rawToken) {
    PasswordResetToken token = findUsable(rawToken);
    if (token == null) {
      throw invalidLink();
    }
  }

  @Transactional
  public void reset(String rawToken, String newPassword, String confirmPassword) {
    if (confirmPassword == null || !confirmPassword.equals(newPassword)) {
      throw ApiException.badRequest("The new passwords do not match");
    }
    PasswordResetToken token = resetTokens
        .findByTokenHashForUpdate(TokenHashes.sha256Hex(normalizeToken(rawToken)))
        .orElseThrow(PasswordResetService::invalidLink);
    if (!token.isUsable()) {
      throw invalidLink();
    }
    AppUser user = token.getUser();
    if (user == null || !user.isActive()) {
      throw invalidLink();
    }
    PasswordPolicy.requireValid(newPassword, user.getEmail());
    if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
      throw ApiException.badRequest("New password must differ from the current password");
    }

    Instant now = Instant.now();
    user.setPasswordHash(passwordEncoder.encode(newPassword));
    user.setLastPasswordChangeAt(now);
    user.setAuthVersion(user.getAuthVersion() + 1);
    users.save(user);

    // Consume this link and invalidate every other outstanding link.
    token.setUsedAt(now);
    resetTokens.save(token);
    resetTokens.invalidateAllForUser(user.getId(), now);

    // All sessions are revoked. The user must sign in again with the new
    // password; this is the safe default for a recovery flow.
    List<RefreshToken> sessions = refreshTokens.findByUserId(user.getId());
    sessions.stream().filter(t -> !t.isRevoked()).forEach(t -> t.setRevoked(true));
    refreshTokens.saveAll(sessions);

    log.info("Password reset completed for user {}", user.getId());
  }

  private PasswordResetToken findUsable(String rawToken) {
    Optional<PasswordResetToken> found = resetTokens
        .findByTokenHash(TokenHashes.sha256Hex(normalizeToken(rawToken)));
    return found.filter(PasswordResetToken::isUsable).orElse(null);
  }

  private static String normalizeToken(String rawToken) {
    return rawToken == null ? "" : rawToken.trim();
  }

  private static String newToken() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static ApiException invalidLink() {
    return ApiException.badRequest("This reset link is invalid or has expired. Request a new one.");
  }
}
