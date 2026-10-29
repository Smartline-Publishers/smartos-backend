package com.smartline.smartos.modules.sales;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
  Optional<Customer> findByIdAndOrganization_Id(UUID id, UUID organizationId);
  Page<Customer> findByOrganization_Id(UUID organizationId, Pageable pageable);
  Page<Customer> findByOrganization_IdAndNameContainingIgnoreCase(UUID organizationId, String name,
      Pageable pageable);
  long countByOrganization_Id(UUID organizationId);
}
