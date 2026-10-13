package com.smartline.smartos.modules.inventory;

import com.smartline.smartos.modules.book.Book;
import com.smartline.smartos.modules.organization.Organization;
import com.smartline.smartos.tenant.BelongsToOrganization;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.data.annotation.CreatedBy;

/**
 * Immutable stock ledger row. Stock levels are always derived as
 * {@code SUM(qty_delta)} grouped by book and warehouse — never stored —
 * so every change carries its reason, reference, actor, and timestamp.
 * The API exposes no update or delete for movements.
 */
@Entity
@Table(name = "stock_movements")
public class StockMovement implements BelongsToOrganization {

  public enum MovementType {
    IN, OUT, ADJUSTMENT
  }

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "organization_id", nullable = false)
  private Organization organization;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "book_id", nullable = false)
  private Book book;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "warehouse_id", nullable = false)
  private Warehouse warehouse;

  @Column(name = "qty_delta", nullable = false)
  private int qtyDelta;

  @Enumerated(EnumType.STRING)
  @Column(name = "movement_type", nullable = false)
  private MovementType type;

  @Column(name = "reference_type", length = 32)
  private String referenceType;

  @Column(name = "reference_id")
  private UUID referenceId;

  @Column(length = 500)
  private String reason;

  @CreatedBy
  @Column(name = "created_by")
  private UUID createdBy;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public StockMovement() {
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public Organization getOrganization() {
    return organization;
  }

  public void setOrganization(Organization organization) {
    this.organization = organization;
  }

  public Book getBook() {
    return book;
  }

  public void setBook(Book book) {
    this.book = book;
  }

  public Warehouse getWarehouse() {
    return warehouse;
  }

  public void setWarehouse(Warehouse warehouse) {
    this.warehouse = warehouse;
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

  public UUID getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(UUID createdBy) {
    this.createdBy = createdBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  @Override
  public UUID getOrganizationId() {
    return organization != null ? organization.getId() : null;
  }
}
