package com.smartline.smartos.modules.organization;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {
  Optional<Organization> findBySlug(String slug);
  boolean existsBySlug(String slug);
  boolean existsByName(String name);
}
