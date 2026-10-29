package com.smartline.smartos.modules.production;

import com.smartline.smartos.modules.production.ProductionJob.JobStatus;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductionJobRepository extends JpaRepository<ProductionJob, UUID> {
  // The controller maps jobs to DTOs (book title) outside the service
  // transaction with open-in-view disabled: fetch the book eagerly here so
  // mapping never hits an uninitialized lazy proxy (LazyInitializationException).
  @EntityGraph(attributePaths = {"book"})
  Optional<ProductionJob> findByIdAndOrganization_Id(UUID id, UUID organizationId);
  @EntityGraph(attributePaths = {"book"})
  Page<ProductionJob> findByOrganization_Id(UUID organizationId, Pageable pageable);
  @EntityGraph(attributePaths = {"book"})
  Page<ProductionJob> findByOrganization_IdAndStatus(UUID organizationId, JobStatus status,
      Pageable pageable);
  @EntityGraph(attributePaths = {"book"})
  Page<ProductionJob> findByOrganization_IdAndBookId(UUID organizationId, UUID bookId,
      Pageable pageable);
  long countByOrganization_IdAndStatusIn(UUID organizationId, Collection<JobStatus> statuses);
}
