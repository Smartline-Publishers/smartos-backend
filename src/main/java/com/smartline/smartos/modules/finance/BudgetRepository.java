package com.smartline.smartos.modules.finance;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetRepository extends JpaRepository<Budget, UUID> {
  Optional<Budget> findByIdAndOrganizationId(UUID id, UUID organizationId);
  Page<Budget> findByOrganizationId(UUID organizationId, Pageable pageable);
}
