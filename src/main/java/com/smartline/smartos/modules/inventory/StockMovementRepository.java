package com.smartline.smartos.modules.inventory;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {
  List<StockMovement> findByOrganization_Id(UUID organizationId);
  Page<StockMovement> findByOrganization_IdOrderByCreatedAtDesc(UUID organizationId,
      Pageable pageable);
  Page<StockMovement> findByOrganization_IdAndBookIdOrderByCreatedAtDesc(UUID organizationId,
      UUID bookId, Pageable pageable);
  Page<StockMovement> findByOrganization_IdAndWarehouseIdOrderByCreatedAtDesc(UUID organizationId,
      UUID warehouseId, Pageable pageable);
  boolean existsByOrganization_IdAndBookId(UUID organizationId, UUID bookId);
  boolean existsByOrganization_IdAndWarehouseId(UUID organizationId, UUID warehouseId);
}
