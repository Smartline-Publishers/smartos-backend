package com.smartline.smartos.modules.production;

import com.smartline.smartos.modules.production.ProductionJob.JobStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductionJobRepository extends JpaRepository<ProductionJob, UUID> {
  Optional<ProductionJob> findByIdAndOrganizationId(UUID id, UUID organizationId);
  Page<ProductionJob> findByOrganizationId(UUID organizationId, Pageable pageable);
  Page<ProductionJob> findByOrganizationIdAndStatus(UUID organizationId, JobStatus status,
      Pageable pageable);
  Page<ProductionJob> findByOrganizationIdAndBookId(UUID organizationId, UUID bookId,
      Pageable pageable);
}
