package com.smartline.smartos.modules.user;

import com.smartline.smartos.security.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public final class UserDtos {

  private UserDtos() {
  }

  public static class CreateUserRequest {
    @Email @NotBlank private String email;
    @NotBlank private String password;
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
  }

  public static class UserResponse {
    private UUID id;
    private UUID organizationId;
    private String email;
    private String fullName;
    private Role role;
    private boolean active;

    public UserResponse() {
    }

    public UserResponse(UUID id, UUID organizationId, String email, String fullName, Role role,
        boolean active) {
      this.id = id;
      this.organizationId = organizationId;
      this.email = email;
      this.fullName = fullName;
      this.role = role;
      this.active = active;
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

    public static UserResponse from(AppUser u) {
      UUID orgId = u.getOrganization() != null ? u.getOrganization().getId() : u.getOrganizationId();
      return new UserResponse(u.getId(), orgId, u.getEmail(), u.getFullName(), u.getRole(), u.isActive());
    }
  }
}
