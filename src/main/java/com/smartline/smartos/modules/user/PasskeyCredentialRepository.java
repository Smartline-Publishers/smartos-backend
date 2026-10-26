package com.smartline.smartos.modules.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasskeyCredentialRepository extends JpaRepository<PasskeyCredential, UUID> {
  List<PasskeyCredential> findByUserIdOrderByCreatedAtAsc(UUID userId);
  Optional<PasskeyCredential> findByCredentialId(String credentialId);
  Optional<PasskeyCredential> findByIdAndUserId(UUID id, UUID userId);
  boolean existsByCredentialId(String credentialId);
  long countByUserId(UUID userId);
}
