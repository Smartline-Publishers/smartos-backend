package com.smartline.smartos.modules.manuscript;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManuscriptEventRepository extends JpaRepository<ManuscriptEvent, UUID> {
  List<ManuscriptEvent> findTop50ByManuscriptIdOrderByCreatedAtDesc(UUID manuscriptId);
  List<ManuscriptEvent> findByManuscriptIdAndCreatedAtBetween(UUID manuscriptId, Instant from,
      Instant to);
}
