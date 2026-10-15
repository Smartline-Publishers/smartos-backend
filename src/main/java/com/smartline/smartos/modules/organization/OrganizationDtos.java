package com.smartline.smartos.modules.organization;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class OrganizationDtos {

  private OrganizationDtos() {
  }

  public static class CreateOrganizationRequest {
    @NotBlank private String name;
    @NotBlank private String slug;

    public CreateOrganizationRequest() {
    }

    public CreateOrganizationRequest(String name, String slug) {
      this.name = name;
      this.slug = slug;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getSlug() {
      return slug;
    }

    public void setSlug(String slug) {
      this.slug = slug;
    }
  }

  public static class OrganizationResponse {
    private UUID id;
    private String name;
    private String slug;
    private String plan;
    private Map<String, Integer> limits;
    private Map<String, Object> settings;
    private String logoUrl;
    private Map<String, Object> theme;
    private Instant createdAt;

    public OrganizationResponse() {
    }

    public OrganizationResponse(UUID id, String name, String slug, String plan,
        Map<String, Integer> limits, Map<String, Object> settings, String logoUrl,
        Map<String, Object> theme, Instant createdAt) {
      this.id = id;
      this.name = name;
      this.slug = slug;
      this.plan = plan;
      this.limits = limits;
      this.settings = settings;
      this.logoUrl = logoUrl;
      this.theme = theme;
      this.createdAt = createdAt;
    }

    public UUID getId() {
      return id;
    }

    public void setId(UUID id) {
      this.id = id;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getSlug() {
      return slug;
    }

    public void setSlug(String slug) {
      this.slug = slug;
    }

    public String getPlan() {
      return plan;
    }

    public void setPlan(String plan) {
      this.plan = plan;
    }

    public Map<String, Integer> getLimits() {
      return limits;
    }

    public void setLimits(Map<String, Integer> limits) {
      this.limits = limits;
    }

    public Map<String, Object> getSettings() {
      return settings;
    }

    public void setSettings(Map<String, Object> settings) {
      this.settings = settings;
    }

    public String getLogoUrl() {
      return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
      this.logoUrl = logoUrl;
    }

    public Map<String, Object> getTheme() {
      return theme;
    }

    public void setTheme(Map<String, Object> theme) {
      this.theme = theme;
    }

    public Instant getCreatedAt() {
      return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
      this.createdAt = createdAt;
    }

    public static OrganizationResponse from(Organization o) {
      Map<String, Integer> limits = o.getPlan() != null
          ? Map.of("maxUsers", o.getPlan().getMaxUsers(), "maxBooks", o.getPlan().getMaxBooks())
          : Map.of();
      return new OrganizationResponse(o.getId(), o.getName(), o.getSlug(),
          o.getPlan() != null ? o.getPlan().name() : null, limits, o.getSettings(),
          o.getLogoUrl(), o.getTheme(), o.getCreatedAt());
    }
  }

  /**
   * Partial organization update. Null fields are left untouched; use
   * {@code DELETE /organizations/current/theme} to reset the custom theme and
   * {@code clearLogo=true} to remove the logo.
   */
  public static class UpdateOrganizationRequest {
    private String name;
    private String slug;
    private String logoUrl;
    private Boolean clearLogo;
    private Map<String, Object> theme;

    public UpdateOrganizationRequest() {
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getSlug() {
      return slug;
    }

    public void setSlug(String slug) {
      this.slug = slug;
    }

    public String getLogoUrl() {
      return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
      this.logoUrl = logoUrl;
    }

    public Boolean getClearLogo() {
      return clearLogo;
    }

    public void setClearLogo(Boolean clearLogo) {
      this.clearLogo = clearLogo;
    }

    public Map<String, Object> getTheme() {
      return theme;
    }

    public void setTheme(Map<String, Object> theme) {
      this.theme = theme;
    }
  }
}
