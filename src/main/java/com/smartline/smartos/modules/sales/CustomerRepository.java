package com.smartline.smartos.modules.sales;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
  Optional<Customer> findByIdAndOrganizationId(UUID id, UUID organizationId);
  Page<Customer> findByOrganizationId(UUID organizationId, Pageable pageable);
  Page<Customer> findByOrganizationIdAndNameContainingIgnoreCase(UUID organizationId, String name,
      Pageable pageable);
}
