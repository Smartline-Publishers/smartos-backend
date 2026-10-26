package com.smartline.smartos.modules.legal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LegalAcceptanceRepository extends JpaRepository<LegalAcceptance, UUID> {

  List<LegalAcceptance> findByUserIdOrderByAcceptedAtDesc(UUID userId);

  Page<LegalAcceptance> findByOrganizationIdOrderByAcceptedAtDesc(UUID organizationId,
      Pageable pageable);
}
