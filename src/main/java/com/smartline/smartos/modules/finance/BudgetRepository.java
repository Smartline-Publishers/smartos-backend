package com.smartline.smartos.modules.finance;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetRepository extends JpaRepository<Budget, UUID> {
  Optional<Budget> findByIdAndOrganization_Id(UUID id, UUID organizationId);
  Page<Budget> findByOrganization_Id(UUID organizationId, Pageable pageable);
}
