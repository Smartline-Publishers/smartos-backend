package com.smartline.smartos.modules.inventory;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

  /** Aggregated stock levels per (book, warehouse) for an organization. */
  @Query("select new com.smartline.smartos.modules.inventory.StockMovementRepository$StockLevelProjection("
      + "m.book.id, m.warehouse.id, coalesce(sum(m.qtyDelta), 0)) "
      + "from StockMovement m where m.organization.id = :orgId "
      + "group by m.book.id, m.warehouse.id")
  List<StockLevelProjection> aggregateLevelsByOrg(@Param("orgId") UUID orgId);

  /** Sum of qty_delta for a single book/warehouse in an organization. */
  @Query("select coalesce(sum(m.qtyDelta), 0) from StockMovement m "
      + "where m.organization.id = :orgId and m.book.id = :bookId "
      + "and m.warehouse.id = :warehouseId")
  int sumQtyDelta(@Param("orgId") UUID orgId, @Param("bookId") UUID bookId,
      @Param("warehouseId") UUID warehouseId);

  record StockLevelProjection(UUID bookId, UUID warehouseId, Long quantity) {
  }
}
