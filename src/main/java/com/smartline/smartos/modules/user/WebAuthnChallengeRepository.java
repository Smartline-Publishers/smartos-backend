package com.smartline.smartos.modules.user;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WebAuthnChallengeRepository extends JpaRepository<WebAuthnChallenge, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from WebAuthnChallenge c where c.challengeHash = :hash")
  Optional<WebAuthnChallenge> findByChallengeHashForUpdate(@Param("hash") String hash);

  @Modifying
  @Query("delete from WebAuthnChallenge c where c.expiresAt < :cutoff")
  int deleteExpired(@Param("cutoff") Instant cutoff);
}
