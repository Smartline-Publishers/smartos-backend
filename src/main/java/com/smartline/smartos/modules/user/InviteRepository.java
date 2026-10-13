package com.smartline.smartos.modules.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InviteRepository extends JpaRepository<Invite, UUID> {
  Optional<Invite> findByTokenHash(String tokenHash);
  List<Invite> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);
  Optional<Invite> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
