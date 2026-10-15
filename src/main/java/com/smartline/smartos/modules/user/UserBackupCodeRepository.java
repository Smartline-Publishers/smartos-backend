package com.smartline.smartos.modules.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserBackupCodeRepository extends JpaRepository<UserBackupCode, UUID> {
  List<UserBackupCode> findByUserId(UUID userId);
  Optional<UserBackupCode> findByUserIdAndCodeHash(UUID userId, String codeHash);
  void deleteByUserId(UUID userId);
}
