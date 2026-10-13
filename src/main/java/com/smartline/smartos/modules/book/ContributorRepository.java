package com.smartline.smartos.modules.book;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContributorRepository extends JpaRepository<Contributor, UUID> {
  Page<Contributor> findByOrganizationId(UUID organizationId, Pageable pageable);
  java.util.Optional<Contributor> findByIdAndOrganizationId(UUID id, UUID organizationId);
  List<Contributor> findByOrganizationId(UUID organizationId);
}
