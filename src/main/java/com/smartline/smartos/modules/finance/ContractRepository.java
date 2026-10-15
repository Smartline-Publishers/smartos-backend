package com.smartline.smartos.modules.finance;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContractRepository extends JpaRepository<Contract, UUID> {
  Optional<Contract> findByIdAndOrganization_Id(UUID id, UUID organizationId);
  Page<Contract> findByOrganization_Id(UUID organizationId, Pageable pageable);
  List<Contract> findByContributorId(UUID contributorId);
  boolean existsByContributorId(UUID contributorId);
  List<Contract> findByOrganization_Id(UUID organizationId);
}
