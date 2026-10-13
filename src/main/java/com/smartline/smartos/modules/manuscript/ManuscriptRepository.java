package com.smartline.smartos.modules.manuscript;

import com.smartline.smartos.modules.manuscript.Manuscript.ManuscriptStage;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManuscriptRepository extends JpaRepository<Manuscript, UUID> {
  Optional<Manuscript> findByIdAndOrganizationId(UUID id, UUID organizationId);
  Page<Manuscript> findByOrganizationId(UUID organizationId, Pageable pageable);
  Page<Manuscript> findByOrganizationIdAndStage(UUID organizationId, ManuscriptStage stage,
      Pageable pageable);
  Page<Manuscript> findByOrganizationIdAndTitleContainingIgnoreCase(UUID organizationId,
      String title, Pageable pageable);
}
