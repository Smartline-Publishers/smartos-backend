package com.smartline.smartos.modules.manuscript;

import com.smartline.smartos.modules.manuscript.Manuscript.ManuscriptStage;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManuscriptRepository extends JpaRepository<Manuscript, UUID> {
  Optional<Manuscript> findByIdAndOrganization_Id(UUID id, UUID organizationId);
  Page<Manuscript> findByOrganization_Id(UUID organizationId, Pageable pageable);
  Page<Manuscript> findByOrganization_IdAndStage(UUID organizationId, ManuscriptStage stage,
      Pageable pageable);
  Page<Manuscript> findByOrganization_IdAndTitleContainingIgnoreCase(UUID organizationId,
      String title, Pageable pageable);
}
