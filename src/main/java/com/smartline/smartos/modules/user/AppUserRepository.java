package com.smartline.smartos.modules.user;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
  Optional<AppUser> findByOrganization_IdAndEmail(UUID organizationId, String email);
  Optional<AppUser> findByOrganization_IdAndEmailIgnoreCase(UUID organizationId, String email);
  boolean existsByOrganization_IdAndEmail(UUID organizationId, String email);
  Page<AppUser> findByOrganization_Id(UUID organizationId, Pageable pageable);
  Optional<AppUser> findByIdAndOrganization_Id(UUID id, UUID organizationId);
  List<AppUser> findByOrganization_IdAndIdIn(UUID organizationId, Collection<UUID> ids);
  List<AppUser> findByEmailIgnoreCase(String email);
  long countByOrganization_Id(UUID organizationId);
}
