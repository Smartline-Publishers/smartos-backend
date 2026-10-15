package com.smartline.smartos.modules.book;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContributorRepository extends JpaRepository<Contributor, UUID> {
  Page<Contributor> findByOrganization_Id(UUID organizationId, Pageable pageable);
  java.util.Optional<Contributor> findByIdAndOrganization_Id(UUID id, UUID organizationId);
  List<Contributor> findByOrganization_Id(UUID organizationId);
}
