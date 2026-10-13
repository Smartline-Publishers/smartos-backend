package com.smartline.smartos.modules.book;

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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;

@Entity
@Table(name = "books",
    uniqueConstraints = @UniqueConstraint(columnNames = {"organization_id", "isbn"}))
public class Book implements BelongsToOrganization {

  public enum BookStatus {
    DRAFT, IN_EDIT, DESIGNED, PRINTING, PUBLISHED, ARCHIVED
  }

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "organization_id", nullable = false)
  private Organization organization;

  // Lengths mirror db/migration/V2__books.sql (Hibernate ddl-auto is validate).
  @Column(nullable = false, length = 500)
  private String title;

  @Column(length = 500)
  private String subtitle;

  @Column(length = 32)
  private String isbn;

  private String imprint;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private BookStatus status = BookStatus.DRAFT;

  @Column(length = 16)
  private String language;

  @Column(name = "page_count")
  private Integer pageCount;

  @CreatedBy
  @Column(name = "created_by")
  private UUID createdBy;

  @LastModifiedBy
  @Column(name = "updated_by")
  private UUID updatedBy;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public Book() {
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

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getSubtitle() {
    return subtitle;
  }

  public void setSubtitle(String subtitle) {
    this.subtitle = subtitle;
  }

  public String getIsbn() {
    return isbn;
  }

  public void setIsbn(String isbn) {
    this.isbn = isbn;
  }

  public String getImprint() {
    return imprint;
  }

  public void setImprint(String imprint) {
    this.imprint = imprint;
  }

  public BookStatus getStatus() {
    return status;
  }

  public void setStatus(BookStatus status) {
    this.status = status;
  }

  public String getLanguage() {
    return language;
  }

  public void setLanguage(String language) {
    this.language = language;
  }

  public Integer getPageCount() {
    return pageCount;
  }

  public void setPageCount(Integer pageCount) {
    this.pageCount = pageCount;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(UUID createdBy) {
    this.createdBy = createdBy;
  }

  public UUID getUpdatedBy() {
    return updatedBy;
  }

  public void setUpdatedBy(UUID updatedBy) {
    this.updatedBy = updatedBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  @Override
  public UUID getOrganizationId() {
    return organization != null ? organization.getId() : null;
  }
}
