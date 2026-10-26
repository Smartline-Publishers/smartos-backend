package com.smartline.smartos.modules.legal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A published version of a legal document.
 *
 * <p>Versions are immutable once published: correcting the wording requires a
 * new version, so an acceptance always refers to exactly what the user saw.
 * {@code current} marks the version new users must accept.
 */
@Entity
@Table(name = "legal_documents",
    uniqueConstraints = @UniqueConstraint(columnNames = {"code", "version"}))
public class LegalDocument {

  public enum Code {
    TERMS_OF_SERVICE,
    PRIVACY_POLICY
  }

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private Code code;

  @Column(nullable = false, length = 32)
  private String version;

  @Column(nullable = false)
  private String title;

  @Column(columnDefinition = "TEXT")
  private String summary;

  @Column(name = "effective_date", nullable = false)
  private LocalDate effectiveDate;

  /** Structured document body: sections, review flags and changelog. */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb", nullable = false)
  private Map<String, Object> content;

  /** SHA-256 of the canonical content payload. */
  @Column(name = "content_hash", nullable = false, length = 64)
  private String contentHash;

  @Column(name = "is_current", nullable = false)
  private boolean current;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public Code getCode() {
    return code;
  }

  public void setCode(Code code) {
    this.code = code;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getSummary() {
    return summary;
  }

  public void setSummary(String summary) {
    this.summary = summary;
  }

  public LocalDate getEffectiveDate() {
    return effectiveDate;
  }

  public void setEffectiveDate(LocalDate effectiveDate) {
    this.effectiveDate = effectiveDate;
  }

  public Map<String, Object> getContent() {
    return content;
  }

  public void setContent(Map<String, Object> content) {
    this.content = content;
  }

  public String getContentHash() {
    return contentHash;
  }

  public void setContentHash(String contentHash) {
    this.contentHash = contentHash;
  }

  public boolean isCurrent() {
    return current;
  }

  public void setCurrent(boolean current) {
    this.current = current;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
