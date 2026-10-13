package com.smartline.smartos.modules.finance;

import com.smartline.smartos.modules.finance.RoyaltyStatement.StatementStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoyaltyStatementRepository extends JpaRepository<RoyaltyStatement, UUID> {
  Optional<RoyaltyStatement> findByIdAndOrganizationId(UUID id, UUID organizationId);
  Page<RoyaltyStatement> findByOrganizationId(UUID organizationId, Pageable pageable);
  Page<RoyaltyStatement> findByOrganizationIdAndStatus(UUID organizationId, StatementStatus status,
      Pageable pageable);
  boolean existsByContractId(UUID contractId);
}
