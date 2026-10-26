package com.smartline.smartos.modules.user;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

  /**
   * Row lock: two concurrent submissions of the same reset link can never
   * both succeed. The loser waits, then observes used_at and is rejected.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from PasswordResetToken t where t.tokenHash = :hash")
  Optional<PasswordResetToken> findByTokenHashForUpdate(@Param("hash") String hash);

  Optional<PasswordResetToken> findByTokenHash(String hash);

  List<PasswordResetToken> findByUserId(UUID userId);

  @Modifying
  @Query("update PasswordResetToken t set t.usedAt = :now where t.user.id = :userId and t.usedAt is null")
  int invalidateAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);
}
