package com.smartline.smartos.modules.inventory;

import com.smartline.smartos.modules.inventory.StockMovement.MovementType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class InventoryDtos {

  private InventoryDtos() {
  }

  public static class CreateWarehouseRequest {
    @NotBlank private String name;
    @Size(max = 500) private String location;

    public CreateWarehouseRequest() {
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getLocation() {
      return location;
    }

    public void setLocation(String location) {
      this.location = location;
    }
  }

  public static class WarehouseResponse {
    private UUID id;
    private UUID organizationId;
    private String name;
    private String location;

    public WarehouseResponse() {
    }

    public WarehouseResponse(UUID id, UUID organizationId, String name, String location) {
      this.id = id;
      this.organizationId = organizationId;
      this.name = name;
      this.location = location;
    }

    public UUID getId() {
      return id;
    }

    public void setId(UUID id) {
      this.id = id;
    }

    public UUID getOrganizationId() {
      return organizationId;
    }

    public void setOrganizationId(UUID organizationId) {
      this.organizationId = organizationId;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getLocation() {
      return location;
    }

    public void setLocation(String location) {
      this.location = location;
    }
  }

  public static class CreateMovementRequest {
    @NotNull private UUID bookId;
    @NotNull private UUID warehouseId;
    @NotNull private Integer qtyDelta;
    @NotNull private MovementType type;
    @Size(max = 500) private String reason;

    public CreateMovementRequest() {
    }

    public UUID getBookId() {
      return bookId;
    }

    public void setBookId(UUID bookId) {
      this.bookId = bookId;
    }

    public UUID getWarehouseId() {
      return warehouseId;
    }

    public void setWarehouseId(UUID warehouseId) {
      this.warehouseId = warehouseId;
    }

    public Integer getQtyDelta() {
      return qtyDelta;
    }

    public void setQtyDelta(Integer qtyDelta) {
      this.qtyDelta = qtyDelta;
    }

    public MovementType getType() {
      return type;
    }

    public void setType(MovementType type) {
      this.type = type;
    }

    public String getReason() {
      return reason;
    }

    public void setReason(String reason) {
      this.reason = reason;
    }
  }

  public static class TransferRequest {
    @NotNull private UUID bookId;
    @NotNull private UUID fromWarehouseId;
    @NotNull private UUID toWarehouseId;
    @NotNull @Min(1) private Integer quantity;
    @Size(max = 500) private String reason;

    public TransferRequest() {
    }

    public UUID getBookId() {
      return bookId;
    }

    public void setBookId(UUID bookId) {
      this.bookId = bookId;
    }

    public UUID getFromWarehouseId() {
      return fromWarehouseId;
    }

    public void setFromWarehouseId(UUID fromWarehouseId) {
      this.fromWarehouseId = fromWarehouseId;
    }

    public UUID getToWarehouseId() {
      return toWarehouseId;
    }

    public void setToWarehouseId(UUID toWarehouseId) {
      this.toWarehouseId = toWarehouseId;
    }

    public Integer getQuantity() {
      return quantity;
    }

    public void setQuantity(Integer quantity) {
      this.quantity = quantity;
    }

    public String getReason() {
      return reason;
    }

    public void setReason(String reason) {
      this.reason = reason;
    }
  }

  public static class MovementResponse {
    private UUID id;
    private UUID organizationId;
    private UUID bookId;
    private UUID warehouseId;
    private int qtyDelta;
    private MovementType type;
    private String referenceType;
    private UUID referenceId;
    private String reason;
    private Instant createdAt;

    public MovementResponse() {
    }

    public UUID getId() {
      return id;
    }

    public void setId(UUID id) {
      this.id = id;
    }

    public UUID getOrganizationId() {
      return organizationId;
    }

    public void setOrganizationId(UUID organizationId) {
      this.organizationId = organizationId;
    }

    public UUID getBookId() {
      return bookId;
    }

    public void setBookId(UUID bookId) {
      this.bookId = bookId;
    }

    public UUID getWarehouseId() {
      return warehouseId;
    }

    public void setWarehouseId(UUID warehouseId) {
      this.warehouseId = warehouseId;
    }

    public int getQtyDelta() {
      return qtyDelta;
    }

    public void setQtyDelta(int qtyDelta) {
      this.qtyDelta = qtyDelta;
    }

    public MovementType getType() {
      return type;
    }

    public void setType(MovementType type) {
      this.type = type;
    }

    public String getReferenceType() {
      return referenceType;
    }

    public void setReferenceType(String referenceType) {
      this.referenceType = referenceType;
    }

    public UUID getReferenceId() {
      return referenceId;
    }

    public void setReferenceId(UUID referenceId) {
      this.referenceId = referenceId;
    }

    public String getReason() {
      return reason;
    }

    public void setReason(String reason) {
      this.reason = reason;
    }

    public Instant getCreatedAt() {
      return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
      this.createdAt = createdAt;
    }
  }

  public static class StockLevelResponse {
    private UUID bookId;
    private String bookTitle;
    private UUID warehouseId;
    private String warehouseName;
    private int quantity;

    public StockLevelResponse() {
    }

    public StockLevelResponse(UUID bookId, String bookTitle, UUID warehouseId,
        String warehouseName, int quantity) {
      this.bookId = bookId;
      this.bookTitle = bookTitle;
      this.warehouseId = warehouseId;
      this.warehouseName = warehouseName;
      this.quantity = quantity;
    }

    public UUID getBookId() {
      return bookId;
    }

    public void setBookId(UUID bookId) {
      this.bookId = bookId;
    }

    public String getBookTitle() {
      return bookTitle;
    }

    public void setBookTitle(String bookTitle) {
      this.bookTitle = bookTitle;
    }

    public UUID getWarehouseId() {
      return warehouseId;
    }

    public void setWarehouseId(UUID warehouseId) {
      this.warehouseId = warehouseId;
    }

    public String getWarehouseName() {
      return warehouseName;
    }

    public void setWarehouseName(String warehouseName) {
      this.warehouseName = warehouseName;
    }

    public int getQuantity() {
      return quantity;
    }

    public void setQuantity(int quantity) {
      this.quantity = quantity;
    }
  }

  public static class InventoryMapper {
    private InventoryMapper() {
    }

    public static WarehouseResponse toResponse(Warehouse w) {
      UUID orgId = w.getOrganization() != null ? w.getOrganization().getId()
          : w.getOrganizationId();
      return new WarehouseResponse(w.getId(), orgId, w.getName(), w.getLocation());
    }

    public static MovementResponse toResponse(StockMovement m) {
      UUID orgId = m.getOrganization() != null ? m.getOrganization().getId()
          : m.getOrganizationId();
      MovementResponse r = new MovementResponse();
      r.setId(m.getId());
      r.setOrganizationId(orgId);
      r.setBookId(m.getBook() != null ? m.getBook().getId() : null);
      r.setWarehouseId(m.getWarehouse() != null ? m.getWarehouse().getId() : null);
      r.setQtyDelta(m.getQtyDelta());
      r.setType(m.getType());
      r.setReferenceType(m.getReferenceType());
      r.setReferenceId(m.getReferenceId());
      r.setReason(m.getReason());
      r.setCreatedAt(m.getCreatedAt());
      return r;
    }
  }
}
