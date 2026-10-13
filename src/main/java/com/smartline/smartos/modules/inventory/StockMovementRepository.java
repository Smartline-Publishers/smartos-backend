package com.smartline.smartos.modules.inventory;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {
  List<StockMovement> findByOrganizationId(UUID organizationId);
  Page<StockMovement> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId,
      Pageable pageable);
  Page<StockMovement> findByOrganizationIdAndBookIdOrderByCreatedAtDesc(UUID organizationId,
      UUID bookId, Pageable pageable);
  Page<StockMovement> findByOrganizationIdAndWarehouseIdOrderByCreatedAtDesc(UUID organizationId,
      UUID warehouseId, Pageable pageable);
  boolean existsByOrganizationIdAndBookId(UUID organizationId, UUID bookId);
  boolean existsByOrganizationIdAndWarehouseId(UUID organizationId, UUID warehouseId);
}
