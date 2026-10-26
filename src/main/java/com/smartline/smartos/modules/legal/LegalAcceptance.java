package com.smartline.smartos.modules.legal;

import com.smartline.smartos.modules.legal.LegalDocument.Code;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Immutable evidence that a user accepted a specific document version.
 *
 * <p>User/organisation references are plain UUIDs rather than JPA relations
 * so the row remains a complete audit record even if other entities change.
 * The version, content hash and request metadata are snapshotted at the moment
 * of acceptance.
 */
@Entity
@Table(name = "legal_acceptances")
public class LegalAcceptance {

  public enum Context {
    REGISTRATION,
    INVITE_ACCEPTANCE
  }

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "document_id", nullable = false)
  private UUID documentId;

  @Enumerated(EnumType.STRING)
  @Column(name = "document_code", nullable = false, length = 32)
  private Code documentCode;

  @Column(name = "document_version", nullable = false, length = 32)
  private String documentVersion;

  @Column(name = "content_hash", nullable = false, length = 64)
  private String contentHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private Context context;

  @Column(name = "invite_id")
  private UUID inviteId;

  @Column(name = "ip_address", length = 64)
  private String ipAddress;

  @Column(name = "user_agent", length = 512)
  private String userAgent;

  @CreationTimestamp
  @Column(name = "accepted_at", nullable = false, updatable = false)
  private Instant acceptedAt;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public void setOrganizationId(UUID organizationId) {
    this.organizationId = organizationId;
  }

  public UUID getDocumentId() {
    return documentId;
  }

  public void setDocumentId(UUID documentId) {
    this.documentId = documentId;
  }

  public Code getDocumentCode() {
    return documentCode;
  }

  public void setDocumentCode(Code documentCode) {
    this.documentCode = documentCode;
  }

  public String getDocumentVersion() {
    return documentVersion;
  }

  public void setDocumentVersion(String documentVersion) {
    this.documentVersion = documentVersion;
  }

  public String getContentHash() {
    return contentHash;
  }

  public void setContentHash(String contentHash) {
    this.contentHash = contentHash;
  }

  public Context getContext() {
    return context;
  }

  public void setContext(Context context) {
    this.context = context;
  }

  public UUID getInviteId() {
    return inviteId;
  }

  public void setInviteId(UUID inviteId) {
    this.inviteId = inviteId;
  }

  public String getIpAddress() {
    return ipAddress;
  }

  public void setIpAddress(String ipAddress) {
    this.ipAddress = ipAddress;
  }

  public String getUserAgent() {
    return userAgent;
  }

  public void setUserAgent(String userAgent) {
    this.userAgent = userAgent;
  }

  public Instant getAcceptedAt() {
    return acceptedAt;
  }
}
