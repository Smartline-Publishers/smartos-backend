package com.smartline.smartos.modules.inventory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {
  Optional<Warehouse> findByIdAndOrganization_Id(UUID id, UUID organizationId);
  List<Warehouse> findByOrganization_IdOrderByName(UUID organizationId);
  boolean existsByOrganization_IdAndName(UUID organizationId, String name);
  long countByOrganization_Id(UUID organizationId);
}
