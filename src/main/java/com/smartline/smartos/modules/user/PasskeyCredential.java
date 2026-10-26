package com.smartline.smartos.modules.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A registered WebAuthn credential. The private key lives only on the
 * authenticator; this row stores the public key and replay counter.
 */
@Entity
@Table(name = "passkey_credentials")
public class PasskeyCredential {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private AppUser user;

  /** Base64url credential id as returned by the authenticator. */
  @Column(name = "credential_id", nullable = false, unique = true, length = 1024)
  private String credentialId;

  /** Raw COSE_Key bytes (public key only). */
  @Column(name = "public_key_cose", nullable = false)
  private byte[] publicKeyCose;

  @Column(name = "sign_count", nullable = false)
  private long signCount;

  @Column(length = 255)
  private String transports;

  @Column(length = 64)
  private String aaguid;

  @Column(nullable = false, length = 100)
  private String nickname;

  @Column(name = "backup_eligible", nullable = false)
  private boolean backupEligible;

  @Column(name = "backup_state", nullable = false)
  private boolean backupState;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "last_used_at")
  private Instant lastUsedAt;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public AppUser getUser() {
    return user;
  }

  public void setUser(AppUser user) {
    this.user = user;
  }

  public String getCredentialId() {
    return credentialId;
  }

  public void setCredentialId(String credentialId) {
    this.credentialId = credentialId;
  }

  public byte[] getPublicKeyCose() {
    return publicKeyCose;
  }

  public void setPublicKeyCose(byte[] publicKeyCose) {
    this.publicKeyCose = publicKeyCose;
  }

  public long getSignCount() {
    return signCount;
  }

  public void setSignCount(long signCount) {
    this.signCount = signCount;
  }

  public String getTransports() {
    return transports;
  }

  public void setTransports(String transports) {
    this.transports = transports;
  }

  public String getAaguid() {
    return aaguid;
  }

  public void setAaguid(String aaguid) {
    this.aaguid = aaguid;
  }

  public String getNickname() {
    return nickname;
  }

  public void setNickname(String nickname) {
    this.nickname = nickname;
  }

  public boolean isBackupEligible() {
    return backupEligible;
  }

  public void setBackupEligible(boolean backupEligible) {
    this.backupEligible = backupEligible;
  }

  public boolean isBackupState() {
    return backupState;
  }

  public void setBackupState(boolean backupState) {
    this.backupState = backupState;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getLastUsedAt() {
    return lastUsedAt;
  }

  public void setLastUsedAt(Instant lastUsedAt) {
    this.lastUsedAt = lastUsedAt;
  }
}
