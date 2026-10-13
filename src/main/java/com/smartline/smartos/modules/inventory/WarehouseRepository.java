package com.smartline.smartos.modules.inventory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {
  Optional<Warehouse> findByIdAndOrganizationId(UUID id, UUID organizationId);
  List<Warehouse> findByOrganizationIdOrderByName(UUID organizationId);
  boolean existsByOrganizationIdAndName(UUID organizationId, String name);
}
