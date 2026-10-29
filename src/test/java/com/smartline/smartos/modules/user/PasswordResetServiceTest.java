package com.smartline.smartos.modules.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smartline.smartos.common.exception.ApiException;
import com.smartline.smartos.modules.user.PasswordResetDtos.ForgotResponse;
import com.smartline.smartos.security.AuthRateLimiter;
import com.smartline.smartos.security.TokenHashes;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

  private static final long TTL_MINUTES = 30;

  @Mock private AppUserRepository users;
  @Mock private PasswordResetTokenRepository resetTokens;
  @Mock private RefreshTokenRepository refreshTokens;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private PasswordResetMailer mailer;
  @Mock private AuthRateLimiter rateLimiter;

  private PasswordResetService service;

  @BeforeEach
  void setUp() {
    service = new PasswordResetService(users, resetTokens, refreshTokens, passwordEncoder,
        mailer, rateLimiter, TTL_MINUTES, true);
    lenient().when(rateLimiter.forgotAllowed(any(), any())).thenReturn(true);
    // These tests exercise the delivery path, so the transport must look ready
    // even when a specific send is then made to fail.
    lenient().when(mailer.isEnabled()).thenReturn(true);
  }

  @Test
  void requestStoresOnlyTheHashAndEmailsTheRawToken() {
    AppUser user = user("trace@test.local");
    when(users.findByEmailIgnoreCase("trace@test.local")).thenReturn(List.of(user));
    when(mailer.send(eq(user), anyString())).thenReturn(true);

    ForgotResponse response = service.requestReset("  Trace@Test.Local ", "203.0.113.5");

    assertTrue(response.isSent());
    assertNotNull(response.getDevToken());
    ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
    verify(resetTokens).save(captor.capture());
    PasswordResetToken stored = captor.getValue();
    assertEquals(TokenHashes.sha256Hex(response.getDevToken()), stored.getTokenHash());
    assertNotEquals(response.getDevToken(), stored.getTokenHash());
    assertEquals(user, stored.getUser());
    assertEquals("203.0.113.5", stored.getRequestedIp());
    assertTrue(stored.getExpiresAt().isAfter(Instant.now().plus(Duration.ofMinutes(TTL_MINUTES - 1))));
    verify(mailer).send(user, response.getDevToken());
    verify(resetTokens).invalidateAllForUser(eq(user.getId()), any(Instant.class));
  }

  @Test
  void failedDeliveryKeepsGenericResponseAndInvalidatesUndeliveredToken() {
    service = new PasswordResetService(users, resetTokens, refreshTokens, passwordEncoder,
        mailer, rateLimiter, TTL_MINUTES, false);
    AppUser user = user("trace@test.local");
    when(users.findByEmailIgnoreCase("trace@test.local")).thenReturn(List.of(user));
    when(mailer.send(eq(user), anyString())).thenReturn(false);

    ForgotResponse response = service.requestReset("trace@test.local", "203.0.113.5");

    assertTrue(response.isSent());
    assertNull(response.getDevToken());
    verify(resetTokens).save(any(PasswordResetToken.class));
    verify(resetTokens, org.mockito.Mockito.times(2))
        .invalidateAllForUser(eq(user.getId()), any(Instant.class));
  }

  @Test
  void requestForUnknownEmailLooksIdenticalAndStoresNothing() {
    when(users.findByEmailIgnoreCase("nobody@test.local")).thenReturn(List.of());

    ForgotResponse response = service.requestReset("nobody@test.local", "203.0.113.5");

    assertTrue(response.isSent());
    assertNull(response.getDevToken());
    verify(resetTokens, never()).save(any());
    verifyNoInteractions(mailer);
  }

  @Test
  void requestIsRateLimitedPerEmailAndIp() {
    when(rateLimiter.forgotAllowed("trace@test.local", "203.0.113.5")).thenReturn(false);

    ApiException ex = assertThrows(ApiException.class,
        () -> service.requestReset("trace@test.local", "203.0.113.5"));

    assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatus());
    verifyNoInteractions(users);
  }

  @Test
  void validateAcceptsLiveTokens() {
    PasswordResetToken token = usableToken(user("trace@test.local"));
    when(resetTokens.findByTokenHash(TokenHashes.sha256Hex("raw-token")))
        .thenReturn(Optional.of(token));

    service.validate(" raw-token ");
  }

  @Test
  void validateRejectsExpiredAndUsedTokens() {
    AppUser user = user("trace@test.local");
    PasswordResetToken expired = usableToken(user);
    expired.setExpiresAt(Instant.now().minusSeconds(1));
    when(resetTokens.findByTokenHash(TokenHashes.sha256Hex("expired")))
        .thenReturn(Optional.of(expired));

    ApiException expiredError = assertThrows(ApiException.class, () -> service.validate("expired"));
    assertEquals(HttpStatus.BAD_REQUEST, expiredError.getStatus());

    PasswordResetToken used = usableToken(user);
    used.setUsedAt(Instant.now());
    when(resetTokens.findByTokenHash(TokenHashes.sha256Hex("used"))).thenReturn(Optional.of(used));

    ApiException usedError = assertThrows(ApiException.class, () -> service.validate("used"));
    assertEquals(HttpStatus.BAD_REQUEST, usedError.getStatus());
  }

  @Test
  void resetChangesThePasswordAndRevokesEverySession() {
    AppUser user = user("trace@test.local");
    PasswordResetToken token = usableToken(user);
    when(resetTokens.findByTokenHashForUpdate(TokenHashes.sha256Hex("raw-token")))
        .thenReturn(Optional.of(token));
    when(passwordEncoder.matches("Publ1shing-Desk!", "old-hash")).thenReturn(false);
    when(passwordEncoder.encode("Publ1shing-Desk!")).thenReturn("new-hash");
    RefreshToken live = refreshToken(user, false);
    RefreshToken alreadyRevoked = refreshToken(user, true);
    when(refreshTokens.findByUserId(user.getId())).thenReturn(List.of(live, alreadyRevoked));

    service.reset(" raw-token ", "Publ1shing-Desk!", "Publ1shing-Desk!");

    assertEquals("new-hash", user.getPasswordHash());
    assertEquals(1, user.getAuthVersion());
    assertNotNull(user.getLastPasswordChangeAt());
    assertNotNull(token.getUsedAt());
    assertTrue(live.isRevoked());
    verify(users).save(user);
    verify(refreshTokens).saveAll(List.of(live, alreadyRevoked));
    verify(resetTokens).invalidateAllForUser(eq(user.getId()), any(Instant.class));
  }

  @Test
  void resetRejectsMismatchedPasswordsBeforeTouchingTheToken() {
    ApiException ex = assertThrows(ApiException.class,
        () -> service.reset("raw-token", "Publ1shing-Desk!", "different-pass-1"));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    verifyNoInteractions(resetTokens);
  }

  @Test
  void resetRejectsAnExpiredOrAlreadyUsedLink() {
    AppUser user = user("trace@test.local");
    PasswordResetToken expired = usableToken(user);
    expired.setExpiresAt(Instant.now().minusSeconds(1));
    when(resetTokens.findByTokenHashForUpdate(TokenHashes.sha256Hex("expired")))
        .thenReturn(Optional.of(expired));

    ApiException error = assertThrows(ApiException.class,
        () -> service.reset("expired", "Publ1shing-Desk!", "Publ1shing-Desk!"));

    assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
    assertEquals("This reset link is invalid or has expired. Request a new one.", error.getMessage());
    verify(users, never()).save(any());
  }

  @Test
  void resetRejectsReuseOfTheSameLink() {
    AppUser user = user("trace@test.local");
    PasswordResetToken used = usableToken(user);
    used.setUsedAt(Instant.now().minusSeconds(30));
    when(resetTokens.findByTokenHashForUpdate(TokenHashes.sha256Hex("used")))
        .thenReturn(Optional.of(used));

    assertThrows(ApiException.class,
        () -> service.reset("used", "Publ1shing-Desk!", "Publ1shing-Desk!"));

    verify(users, never()).save(any());
  }

  @Test
  void resetRejectsTheCurrentPasswordAndWeakPasswords() {
    AppUser user = user("trace@test.local");
    PasswordResetToken token = usableToken(user);
    when(resetTokens.findByTokenHashForUpdate(TokenHashes.sha256Hex("raw-token")))
        .thenReturn(Optional.of(token));
    when(passwordEncoder.matches("Current-Pass-1", "old-hash")).thenReturn(true);

    ApiException same = assertThrows(ApiException.class,
        () -> service.reset("raw-token", "Current-Pass-1", "Current-Pass-1"));
    assertEquals(HttpStatus.BAD_REQUEST, same.getStatus());

    ApiException weak = assertThrows(ApiException.class,
        () -> service.reset("raw-token", "short", "short"));
    assertEquals(HttpStatus.BAD_REQUEST, weak.getStatus());

    verify(users, never()).save(any());
    assertNull(token.getUsedAt());
  }

  @Test
  void resetRejectsInactiveAccounts() {
    AppUser user = user("trace@test.local");
    user.setActive(false);
    PasswordResetToken token = usableToken(user);
    when(resetTokens.findByTokenHashForUpdate(TokenHashes.sha256Hex("raw-token")))
        .thenReturn(Optional.of(token));

    assertThrows(ApiException.class,
        () -> service.reset("raw-token", "Publ1shing-Desk!", "Publ1shing-Desk!"));

    verify(users, never()).save(any());
  }

  @Test
  void resetCanHideTheDevTokenWhenDisabled() {
    PasswordResetService production = new PasswordResetService(users, resetTokens, refreshTokens,
        passwordEncoder, mailer, rateLimiter, TTL_MINUTES, false);
    AppUser user = user("trace@test.local");
    when(users.findByEmailIgnoreCase("trace@test.local")).thenReturn(List.of(user));
    when(mailer.send(eq(user), anyString())).thenReturn(false);

    ForgotResponse response = production.requestReset("trace@test.local", "203.0.113.5");

    assertTrue(response.isSent());
    assertNull(response.getDevToken());
  }

  private PasswordResetToken usableToken(AppUser user) {
    PasswordResetToken token = new PasswordResetToken();
    token.setId(UUID.randomUUID());
    token.setUser(user);
    token.setTokenHash(TokenHashes.sha256Hex("raw-token"));
    token.setExpiresAt(Instant.now().plusSeconds(600));
    token.setCreatedAt(Instant.now());
    return token;
  }

  private AppUser user(String email) {
    AppUser user = new AppUser();
    user.setId(UUID.randomUUID());
    user.setEmail(email);
    user.setPasswordHash("old-hash");
    user.setFullName("Trace User");
    user.setActive(true);
    return user;
  }

  private RefreshToken refreshToken(AppUser user, boolean revoked) {
    RefreshToken token = new RefreshToken();
    token.setId(UUID.randomUUID());
    token.setUser(user);
    token.setTokenHash(UUID.randomUUID().toString());
    token.setExpiresAt(Instant.now().plusSeconds(3600));
    token.setRevoked(revoked);
    return token;
  }
}
