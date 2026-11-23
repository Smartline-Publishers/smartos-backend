package com.smartline.smartos.modules.user;

import com.smartline.smartos.modules.organization.Organization;
import com.smartline.smartos.security.Role;
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
@Table(name = "app_users",
    uniqueConstraints = @UniqueConstraint(columnNames = {"organization_id", "email"}))
public class AppUser implements BelongsToOrganization {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = true)
  @JoinColumn(name = "organization_id", nullable = true)
  private Organization organization;

  @Column(nullable = false)
  private String email;

  /** Clerk user id (stable external id) once the account is linked to Clerk. */
  @Column(name = "clerk_user_id", length = 255, unique = true)
  private String clerkUserId;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Column(name = "full_name", nullable = false)
  private String fullName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Role role = Role.VIEWER;

  @Column(nullable = false)
  private boolean active = true;

  /** Profile picture: https URL or data:image/* URL managed by the user. */
  @Column(name = "avatar_url", columnDefinition = "TEXT")
  private String avatarUrl;

  /** TOTP secret for two-factor authentication (null until 2FA setup). */
  @Column(name = "totp_secret", length = 128)
  private String totpSecret;

  @Column(name = "totp_enabled", nullable = false)
  private boolean totpEnabled = false;

  /**
   * Monotonic token generation. Access and refresh tokens carry this value;
   * bumping it invalidates every token issued before a password change/reset.
   */
  @Column(name = "auth_version", nullable = false)
  private int authVersion = 0;

  @Column(name = "last_password_change_at")
  private Instant lastPasswordChangeAt;

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

  public AppUser() {
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

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getClerkUserId() {
    return clerkUserId;
  }

  public void setClerkUserId(String clerkUserId) {
    this.clerkUserId = clerkUserId;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public String getFullName() {
    return fullName;
  }

  public void setFullName(String fullName) {
    this.fullName = fullName;
  }

  public Role getRole() {
    return role;
  }

  public void setRole(Role role) {
    this.role = role;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public String getAvatarUrl() {
    return avatarUrl;
  }

  public void setAvatarUrl(String avatarUrl) {
    this.avatarUrl = avatarUrl;
  }

  public String getTotpSecret() {
    return totpSecret;
  }

  public void setTotpSecret(String totpSecret) {
    this.totpSecret = totpSecret;
  }

  public boolean isTotpEnabled() {
    return totpEnabled;
  }

  public void setTotpEnabled(boolean totpEnabled) {
    this.totpEnabled = totpEnabled;
  }

  public int getAuthVersion() {
    return authVersion;
  }

  public void setAuthVersion(int authVersion) {
    this.authVersion = authVersion;
  }

  public Instant getLastPasswordChangeAt() {
    return lastPasswordChangeAt;
  }

  public void setLastPasswordChangeAt(Instant lastPasswordChangeAt) {
    this.lastPasswordChangeAt = lastPasswordChangeAt;
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
