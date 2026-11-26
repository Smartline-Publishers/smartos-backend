package com.smartline.smartos.modules.inventory;

import com.smartline.smartos.common.exception.ApiException;
import com.smartline.smartos.modules.audit.Audited;
import com.smartline.smartos.modules.book.Book;
import com.smartline.smartos.modules.book.BookRepository;
import com.smartline.smartos.modules.inventory.InventoryDtos.CreateMovementRequest;
import com.smartline.smartos.modules.inventory.InventoryDtos.CreateWarehouseRequest;
import com.smartline.smartos.modules.inventory.InventoryDtos.StockLevelResponse;
import com.smartline.smartos.modules.inventory.InventoryDtos.TransferRequest;
import com.smartline.smartos.modules.inventory.StockMovement.MovementType;
import com.smartline.smartos.modules.organization.Organization;
import com.smartline.smartos.modules.organization.OrganizationRepository;
import com.smartline.smartos.security.Role;
import com.smartline.smartos.security.UserPrincipal;
import com.smartline.smartos.tenant.TenantContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

  private final StockMovementRepository movements;
  private final WarehouseRepository warehouses;
  private final BookRepository books;
  private final OrganizationRepository organizations;

  public InventoryService(StockMovementRepository movements, WarehouseRepository warehouses,
      BookRepository books, OrganizationRepository organizations) {
    this.movements = movements;
    this.warehouses = warehouses;
    this.books = books;
    this.organizations = organizations;
  }

  @Transactional
  @Audited(action = "warehouse.create", entityType = "Warehouse")
  public Warehouse createWarehouse(CreateWarehouseRequest req) {
    UUID orgId = TenantContext.require();
    Organization org = organizations.findById(orgId)
        .orElseThrow(() -> ApiException.notFound("Organization", orgId));
    String name = req.getName().trim();
    if (warehouses.existsByOrganization_IdAndName(orgId, name)) {
      throw ApiException.conflict("Warehouse name already exists in organization: " + name);
    }
    Warehouse warehouse = new Warehouse();
    warehouse.setOrganization(org);
    warehouse.setName(name);
    warehouse.setLocation(req.getLocation());
    return warehouses.save(warehouse);
  }

  @Transactional(readOnly = true)
  public List<Warehouse> listWarehouses() {
    return warehouses.findByOrganization_IdOrderByName(TenantContext.require());
  }

  @Transactional(readOnly = true)
  public Warehouse getWarehouse(UUID id) {
    return warehouses.findByIdAndOrganization_Id(id, TenantContext.require())
        .orElseThrow(() -> ApiException.notFound("Warehouse", id));
  }

  @Transactional
  @Audited(action = "warehouse.delete", entityType = "Warehouse")
  public void deleteWarehouse(UUID id) {
    UUID orgId = TenantContext.require();
    Warehouse warehouse = getWarehouse(id);
    if (movements.existsByOrganization_IdAndWarehouseId(orgId, id)) {
      throw ApiException.conflict("Warehouse has stock history and cannot be deleted");
    }
    warehouses.delete(warehouse);
  }

  @Transactional
  @Audited(action = "stock.movement", entityType = "StockMovement")
  public StockMovement recordMovement(CreateMovementRequest req) {
    return recordMovement(req.getBookId(), req.getWarehouseId(), req.getQtyDelta(), req.getType(),
        null, null, req.getReason());
  }

  /**
   * Core ledger write used by the API, transfers, and production posting.
   * Runs in the caller's transaction so multi-row operations stay atomic.
   */
  @Transactional
  public StockMovement recordMovement(UUID bookId, UUID warehouseId, int qtyDelta,
      MovementType type, String referenceType, UUID referenceId, String reason) {
    UUID orgId = TenantContext.require();
    if (qtyDelta == 0) {
      throw ApiException.badRequest("Movement quantity must not be zero");
    }
    if (type == MovementType.IN && qtyDelta < 0
        || type == MovementType.OUT && qtyDelta > 0) {
      throw ApiException.badRequest("Movement sign must match its type");
    }
    Book book = books.findByIdAndOrganization_Id(bookId, orgId)
        .orElseThrow(() -> ApiException.notFound("Book", bookId));
    Warehouse warehouse = warehouses.findByIdAndOrganization_Id(warehouseId, orgId)
        .orElseThrow(() -> ApiException.notFound("Warehouse", warehouseId));
    int resulting = currentLevel(orgId, bookId, warehouseId) + qtyDelta;
    if (resulting < 0 && !(type == MovementType.ADJUSTMENT && callerIsAdmin())) {
      throw ApiException.conflict(
          "Insufficient stock: movement would take below zero (use an ADJUSTMENT as admin)");
    }
    StockMovement movement = new StockMovement();
    movement.setOrganization(book.getOrganization());
    movement.setBook(book);
    movement.setWarehouse(warehouse);
    movement.setQtyDelta(qtyDelta);
    movement.setType(type);
    movement.setReferenceType(referenceType);
    movement.setReferenceId(referenceId);
    movement.setReason(reason);
    return movements.save(movement);
  }

  @Transactional
  @Audited(action = "stock.transfer", entityType = "StockMovement")
  public List<StockMovement> transfer(TransferRequest req) {
    if (req.getFromWarehouseId().equals(req.getToWarehouseId())) {
      throw ApiException.badRequest("Transfer warehouses must differ");
    }
    UUID transferId = UUID.randomUUID();
    String reason = req.getReason() != null ? req.getReason() : "Transfer";
    List<StockMovement> legs = new ArrayList<>(2);
    legs.add(recordMovement(req.getBookId(), req.getFromWarehouseId(), -req.getQuantity(),
        MovementType.OUT, "TRANSFER", transferId, reason));
    legs.add(recordMovement(req.getBookId(), req.getToWarehouseId(), req.getQuantity(),
        MovementType.IN, "TRANSFER", transferId, reason));
    return legs;
  }

  @Transactional(readOnly = true)
  public Page<StockMovement> listMovements(UUID bookId, UUID warehouseId, Pageable pageable) {
    UUID orgId = TenantContext.require();
    if (bookId != null) {
      return movements.findByOrganization_IdAndBookIdOrderByCreatedAtDesc(orgId, bookId, pageable);
    }
    if (warehouseId != null) {
      return movements.findByOrganization_IdAndWarehouseIdOrderByCreatedAtDesc(orgId, warehouseId,
          pageable);
    }
    return movements.findByOrganization_IdOrderByCreatedAtDesc(orgId, pageable);
  }

  @Transactional(readOnly = true)
  public List<StockLevelResponse> levels(UUID bookId, UUID warehouseId, Integer below) {
    UUID orgId = TenantContext.require();
    // Aggregate in the database (GROUP BY) instead of loading every movement row.
    Map<List<UUID>, Integer> sums = new HashMap<>();
    List<StockMovementRepository.StockLevelProjection> aggregated = movements.aggregateLevelsByOrg(orgId);
    if (aggregated == null) aggregated = List.of();
    for (StockMovementRepository.StockLevelProjection p : aggregated) {
      UUID b = p.bookId();
      UUID w = p.warehouseId();
      if (b == null || w == null) continue;
      if (bookId != null && !bookId.equals(b) || warehouseId != null && !warehouseId.equals(w)) {
        continue;
      }
      sums.put(List.of(b, w), p.quantity() == null ? 0 : p.quantity().intValue());
    }
    Map<UUID, String> bookNames = new HashMap<>();
    books.findAllById(sums.keySet().stream().map(k -> k.get(0)).toList())
        .forEach(b -> bookNames.put(b.getId(), b.getTitle()));
    Map<UUID, String> warehouseNames = new HashMap<>();
    warehouses.findAllById(sums.keySet().stream().map(k -> k.get(1)).toList())
        .forEach(w -> warehouseNames.put(w.getId(), w.getName()));
    return sums.entrySet().stream()
        .map(e -> new StockLevelResponse(e.getKey().get(0),
            bookNames.getOrDefault(e.getKey().get(0), "?"),
            e.getKey().get(1), warehouseNames.getOrDefault(e.getKey().get(1), "?"),
            e.getValue()))
        .filter(l -> below == null || l.getQuantity() < below)
        .sorted(Comparator.comparing(StockLevelResponse::getBookTitle)
            .thenComparing(StockLevelResponse::getWarehouseName))
        .toList();
  }

  @Transactional(readOnly = true)
  public int currentLevel(UUID orgId, UUID bookId, UUID warehouseId) {
    // Summed on read so levels can never drift from the ledger. For very
    // large histories this should move to a materialized query.
    return movements.sumQtyDelta(orgId, bookId, warehouseId);
  }

  private boolean callerIsAdmin() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
      return principal.getRoles().contains(Role.ORG_ADMIN.name())
          || principal.getRoles().contains(Role.PLATFORM_ADMIN.name());
    }
    return false;
  }
}
