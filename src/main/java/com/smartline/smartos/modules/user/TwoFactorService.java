package com.smartline.smartos.modules.user;

import com.smartline.smartos.common.exception.ApiException;
import com.smartline.smartos.security.TokenHashes;
import com.smartline.smartos.security.TotpService;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * TOTP two-factor authentication. Setup is two-step (begin stores an
 * unverified secret, confirm verifies a code before enabling) so a mistyped
 * secret can never lock the administrator out. Recovery uses single-use
 * backup codes whose hashes alone are persisted.
 */
@Service
public class TwoFactorService {

  private static final int BACKUP_CODE_COUNT = 10;
  private static final String BACKUP_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
  private static final String ISSUER = "SmartOS";

  private final AppUserRepository users;
  private final UserBackupCodeRepository codes;
  private final TotpService totp;
  private final PasswordEncoder passwordEncoder;
  private final SecureRandom random = new SecureRandom();

  public TwoFactorService(AppUserRepository users, UserBackupCodeRepository codes,
      TotpService totp, PasswordEncoder passwordEncoder) {
    this.users = users;
    this.codes = codes;
    this.totp = totp;
    this.passwordEncoder = passwordEncoder;
  }

  public record SetupResponse(String secret, String otpauthUrl) {
  }

  public record ConfirmResponse(List<String> backupCodes) {
  }

  public record StatusResponse(boolean enabled, int remainingBackupCodes) {
  }

  @Transactional
  public SetupResponse beginSetup(UUID userId) {
    AppUser user = activeUser(userId);
    if (user.isTotpEnabled()) {
      throw ApiException.conflict("Two-factor authentication is already enabled");
    }
    String secret = totp.generateSecret();
    user.setTotpSecret(secret);
    users.save(user);
    return new SetupResponse(secret, totp.otpauthUrl(ISSUER, user.getEmail(), secret));
  }

  @Transactional
  public ConfirmResponse confirmSetup(UUID userId, String code) {
    AppUser user = activeUser(userId);
    if (user.isTotpEnabled()) {
      throw ApiException.conflict("Two-factor authentication is already enabled");
    }
    if (user.getTotpSecret() == null || !totp.verify(user.getTotpSecret(), code)) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
          "Invalid verification code");
    }
    user.setTotpEnabled(true);
    users.save(user);
    codes.deleteByUserId(user.getId());
    List<String> plain = new ArrayList<>(BACKUP_CODE_COUNT);
    for (int i = 0; i < BACKUP_CODE_COUNT; i++) {
      String backup = newBackupCode();
      UserBackupCode record = new UserBackupCode();
      record.setUser(user);
      record.setCodeHash(TokenHashes.sha256Hex(backup));
      codes.save(record);
      plain.add(backup);
    }
    return new ConfirmResponse(List.copyOf(plain));
  }

  @Transactional
  public void disable(UUID userId, String currentPassword) {
    AppUser user = activeUser(userId);
    if (!user.isTotpEnabled()) {
      throw ApiException.badRequest("Two-factor authentication is not enabled");
    }
    if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Password is incorrect");
    }
    user.setTotpEnabled(false);
    user.setTotpSecret(null);
    users.save(user);
    codes.deleteByUserId(user.getId());
  }

  @Transactional(readOnly = true)
  public StatusResponse status(UUID userId) {
    AppUser user = activeUser(userId);
    long remaining = codes.findByUserId(user.getId()).stream().filter(c -> !c.isUsed()).count();
    return new StatusResponse(user.isTotpEnabled(), (int) remaining);
  }

  /**
   * Verifies a login challenge code: either the current TOTP or an unused
   * backup code (consumed on success).
   */
  @Transactional
  public boolean verifyChallenge(AppUser user, String code) {
    if (!user.isTotpEnabled() || code == null) {
      return false;
    }
    if (user.getTotpSecret() != null && totp.verify(user.getTotpSecret(), code.trim())) {
      return true;
    }
    String normalized = code.trim().toUpperCase(java.util.Locale.ROOT);
    return codes.findByUserIdAndCodeHash(user.getId(), TokenHashes.sha256Hex(normalized))
        .filter(c -> !c.isUsed())
        .map(c -> {
          c.setUsed(true);
          codes.save(c);
          return true;
        })
        .orElse(false);
  }

  private AppUser activeUser(UUID userId) {
    AppUser user = users.findById(userId)
        .orElseThrow(() -> ApiException.notFound("User", userId));
    if (!user.isActive()) {
      throw ApiException.forbidden("User account is disabled");
    }
    return user;
  }

  private String newBackupCode() {
    StringBuilder sb = new StringBuilder(9);
    for (int i = 0; i < 8; i++) {
      if (i == 4) {
        sb.append('-');
      }
      sb.append(BACKUP_ALPHABET.charAt(random.nextInt(BACKUP_ALPHABET.length())));
    }
    return sb.toString();
  }
}
