package com.smartline.smartos.modules.user;

import com.smartline.smartos.security.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class UserDtos {

  private UserDtos() {
  }

  public static class CreateUserRequest {
    @Email @NotBlank private String email;
    @NotBlank @Size(min = 8, message = "Password must be at least 8 characters") private String password;
    @NotBlank private String fullName;
    private Role role;

    public CreateUserRequest() {
    }

    public CreateUserRequest(String email, String password, String fullName, Role role) {
      this.email = email;
      this.password = password;
      this.fullName = fullName;
      this.role = role;
    }

    public String getEmail() {
      return email;
    }

    public void setEmail(String email) {
      this.email = email;
    }

    public String getPassword() {
      return password;
    }

    public void setPassword(String password) {
      this.password = password;
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
  }

  public static class UpdateUserRequest {
    private String fullName;
    private Role role;
    private Boolean active;
    private String avatarUrl;

    public UpdateUserRequest() {
    }

    public UpdateUserRequest(String fullName, Role role, Boolean active) {
      this.fullName = fullName;
      this.role = role;
      this.active = active;
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

    public Boolean getActive() {
      return active;
    }

    public void setActive(Boolean active) {
      this.active = active;
    }

    public String getAvatarUrl() {
      return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
      this.avatarUrl = avatarUrl;
    }
  }

  /** Self-service profile update: name and photo only, never role/status. */
  public static class UpdateProfileRequest {
    private String fullName;
    private String avatarUrl;
    private Boolean clearAvatar;

    public UpdateProfileRequest() {
    }

    public String getFullName() {
      return fullName;
    }

    public void setFullName(String fullName) {
      this.fullName = fullName;
    }

    public String getAvatarUrl() {
      return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
      this.avatarUrl = avatarUrl;
    }

    public Boolean getClearAvatar() {
      return clearAvatar;
    }

    public void setClearAvatar(Boolean clearAvatar) {
      this.clearAvatar = clearAvatar;
    }
  }

  public static class ChangePasswordRequest {
    @NotBlank private String currentPassword;
    @NotBlank @Size(min = 10, message = "Password must be at least 10 characters")
    private String newPassword;
    /** Optional client-side confirmation; when present it must match. */
    private String confirmPassword;
    /**
     * Keep the browser that made the change signed in by issuing a fresh
     * token pair. Every other refresh token is revoked either way.
     */
    private boolean keepCurrentSession = true;

    public ChangePasswordRequest() {
    }

    public String getCurrentPassword() {
      return currentPassword;
    }

    public void setCurrentPassword(String currentPassword) {
      this.currentPassword = currentPassword;
    }

    public String getNewPassword() {
      return newPassword;
    }

    public void setNewPassword(String newPassword) {
      this.newPassword = newPassword;
    }

    public String getConfirmPassword() {
      return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
      this.confirmPassword = confirmPassword;
    }

    public boolean isKeepCurrentSession() {
      return keepCurrentSession;
    }

    public void setKeepCurrentSession(boolean keepCurrentSession) {
      this.keepCurrentSession = keepCurrentSession;
    }
  }

  /** Fresh tokens returned when the caller chose to keep the current session. */
  public static class PasswordChangeResponse {
    private String accessToken;
    private String refreshToken;

    public PasswordChangeResponse() {
    }

    public PasswordChangeResponse(String accessToken, String refreshToken) {
      this.accessToken = accessToken;
      this.refreshToken = refreshToken;
    }

    public String getAccessToken() {
      return accessToken;
    }

    public void setAccessToken(String accessToken) {
      this.accessToken = accessToken;
    }

    public String getRefreshToken() {
      return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
      this.refreshToken = refreshToken;
    }
  }

  public static class SessionResponse {
    private UUID id;
    private Instant createdAt;
    private Instant expiresAt;
    private boolean revoked;
    private boolean current;

    public SessionResponse() {
    }

    public SessionResponse(UUID id, Instant createdAt, Instant expiresAt, boolean revoked,
        boolean current) {
      this.id = id;
      this.createdAt = createdAt;
      this.expiresAt = expiresAt;
      this.revoked = revoked;
      this.current = current;
    }

    public UUID getId() {
      return id;
    }

    public void setId(UUID id) {
      this.id = id;
    }

    public Instant getCreatedAt() {
      return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
      this.createdAt = createdAt;
    }

    public Instant getExpiresAt() {
      return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
      this.expiresAt = expiresAt;
    }

    public boolean isRevoked() {
      return revoked;
    }

    public void setRevoked(boolean revoked) {
      this.revoked = revoked;
    }

    public boolean isCurrent() {
      return current;
    }

    public void setCurrent(boolean current) {
      this.current = current;
    }
  }

  public static class UserResponse {
    private UUID id;
    private UUID organizationId;
    private String email;
    private String fullName;
    private Role role;
    private boolean active;
    private String avatarUrl;
    private boolean totpEnabled;

    public UserResponse() {
    }

    public UserResponse(UUID id, UUID organizationId, String email, String fullName, Role role,
        boolean active, String avatarUrl, boolean totpEnabled) {
      this.id = id;
      this.organizationId = organizationId;
      this.email = email;
      this.fullName = fullName;
      this.role = role;
      this.active = active;
      this.avatarUrl = avatarUrl;
      this.totpEnabled = totpEnabled;
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

    public String getEmail() {
      return email;
    }

    public void setEmail(String email) {
      this.email = email;
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

    public boolean isTotpEnabled() {
      return totpEnabled;
    }

    public void setTotpEnabled(boolean totpEnabled) {
      this.totpEnabled = totpEnabled;
    }

    public static UserResponse from(AppUser u) {
      UUID orgId = u.getOrganization() != null ? u.getOrganization().getId() : u.getOrganizationId();
      return new UserResponse(u.getId(), orgId, u.getEmail(), u.getFullName(), u.getRole(),
          u.isActive(), u.getAvatarUrl(), u.isTotpEnabled());
    }
  }
}
