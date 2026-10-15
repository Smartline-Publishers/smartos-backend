package com.smartline.smartos.modules.production;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrintRunRepository extends JpaRepository<PrintRun, UUID> {
  List<PrintRun> findByJobId(UUID jobId);
  Optional<PrintRun> findByIdAndJob_Organization_Id(UUID id, UUID organizationId);
  boolean existsByJobIdAndPostedFalse(UUID jobId);
  boolean existsByJobIdAndPostedTrue(UUID jobId);
}
