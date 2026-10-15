package com.smartline.smartos.modules.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.smartline.smartos.common.exception.ApiException;
import com.smartline.smartos.security.Role;
import com.smartline.smartos.security.TotpService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class TwoFactorServiceTest {

  @Mock private AppUserRepository users;
  @Mock private UserBackupCodeRepository codes;
  @Mock private PasswordEncoder passwordEncoder;

  private TotpService totp = new TotpService();
  private TwoFactorService service;
  private AppUser user;

  @BeforeEach
  void setUp() {
    service = new TwoFactorService(users, codes, totp, passwordEncoder);
    user = new AppUser();
    user.setId(UUID.randomUUID());
    user.setEmail("admin@acme.test");
    user.setPasswordHash("hash");
    user.setFullName("Admin User");
    user.setRole(Role.ORG_ADMIN);
    user.setActive(true);
    when(users.findById(user.getId())).thenReturn(Optional.of(user));
  }

  @Test
  void setupThenConfirmEnablesWithBackupCodes() {
    TwoFactorService.SetupResponse setup = service.beginSetup(user.getId());

    assertTrue(setup.secret().length() >= 16);
    assertTrue(setup.otpauthUrl().startsWith("otpauth://totp/"));
    assertFalse(user.isTotpEnabled());

    String code = totp.codeAt(user.getTotpSecret(), Instant.now());
    when(codes.save(any(UserBackupCode.class))).thenAnswer(inv -> inv.getArgument(0));

    TwoFactorService.ConfirmResponse confirm = service.confirmSetup(user.getId(), code);

    assertTrue(user.isTotpEnabled());
    assertEquals(10, confirm.backupCodes().size());
  }

  @Test
  void confirmWithWrongCodeIsRejected() {
    service.beginSetup(user.getId());

    ApiException ex = assertThrows(ApiException.class,
        () -> service.confirmSetup(user.getId(), "000000"));

    assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
    assertFalse(user.isTotpEnabled());
  }

  @Test
  void setupWhileEnabledIsRejected() {
    user.setTotpEnabled(true);

    assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
        () -> service.beginSetup(user.getId())).getStatus());
  }

  @Test
  void challengeAcceptsTotpAndConsumesBackupCode() {
    service.beginSetup(user.getId());
    String code = totp.codeAt(user.getTotpSecret(), Instant.now());
    when(codes.save(any(UserBackupCode.class))).thenAnswer(inv -> inv.getArgument(0));
    List<String> backup = service.confirmSetup(user.getId(), code).backupCodes();

    assertTrue(service.verifyChallenge(user, code));

    String spent = backup.get(0);
    UserBackupCode record = new UserBackupCode();
    record.setId(UUID.randomUUID());
    when(codes.findByUserIdAndCodeHash(eq(user.getId()), any(String.class)))
        .thenReturn(Optional.of(record));

    assertTrue(service.verifyChallenge(user, spent));
    assertTrue(record.isUsed());
  }

  @Test
  void disableRequiresPassword() {
    user.setTotpEnabled(true);
    when(passwordEncoder.matches("secret123", "hash")).thenReturn(true);

    service.disable(user.getId(), "secret123");

    assertFalse(user.isTotpEnabled());
  }

  @Test
  void disableWithWrongPasswordIsRejected() {
    user.setTotpEnabled(true);
    when(passwordEncoder.matches("nope", "hash")).thenReturn(false);

    assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ApiException.class,
        () -> service.disable(user.getId(), "nope")).getStatus());
    assertTrue(user.isTotpEnabled());
  }
}
