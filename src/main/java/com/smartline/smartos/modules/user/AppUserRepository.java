package com.smartline.smartos.modules.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
  Optional<AppUser> findByOrganizationIdAndEmail(UUID organizationId, String email);
  boolean existsByOrganizationIdAndEmail(UUID organizationId, String email);
  Page<AppUser> findByOrganizationId(UUID organizationId, Pageable pageable);
  Optional<AppUser> findByIdAndOrganizationId(UUID id, UUID organizationId);
  List<AppUser> findByEmailIgnoreCase(String email);
}
