package com.smartline.smartos.modules.user;

import com.smartline.smartos.modules.organization.Organization;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Persisted onboarding checklist state per administrator. Step completion
 * itself is derived live from organization/user state (logo, profile, theme,
 * 2FA); only explicit choices (skipping the optional theme step, dismissing
 * the checklist) are stored here.
 */
@Entity
@Table(name = "onboarding_progress")
public class OnboardingProgress {

  @Id
  private UUID userId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @MapsId
  @JoinColumn(name = "user_id", nullable = false)
  private AppUser user;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "organization_id", nullable = false)
  private Organization organization;

  @Column(name = "theme_skipped", nullable = false)
  private boolean themeSkipped = false;

  @Column(nullable = false)
  private boolean dismissed = false;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public OnboardingProgress() {
  }

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public AppUser getUser() {
    return user;
  }

  public void setUser(AppUser user) {
    this.user = user;
  }

  public Organization getOrganization() {
    return organization;
  }

  public void setOrganization(Organization organization) {
    this.organization = organization;
  }

  public boolean isThemeSkipped() {
    return themeSkipped;
  }

  public void setThemeSkipped(boolean themeSkipped) {
    this.themeSkipped = themeSkipped;
  }

  public boolean isDismissed() {
    return dismissed;
  }

  public void setDismissed(boolean dismissed) {
    this.dismissed = dismissed;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
