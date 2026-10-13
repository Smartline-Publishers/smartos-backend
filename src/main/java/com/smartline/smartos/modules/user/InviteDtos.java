package com.smartline.smartos.modules.user;

import com.smartline.smartos.security.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class InviteDtos {

  private InviteDtos() {
  }

  public static class CreateInviteRequest {
    @Email @NotBlank private String email;
    @NotNull private Role role;
    @Min(1) @Max(30) private Integer daysValid;

    public CreateInviteRequest() {
    }

    public String getEmail() {
      return email;
    }

    public void setEmail(String email) {
      this.email = email;
    }

    public Role getRole() {
      return role;
    }

    public void setRole(Role role) {
      this.role = role;
    }

    public Integer getDaysValid() {
      return daysValid;
    }

    public void setDaysValid(Integer daysValid) {
      this.daysValid = daysValid;
    }
  }

  public static class AcceptInviteRequest {
    @NotBlank private String token;
    @NotBlank private String fullName;
    @NotBlank @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    public AcceptInviteRequest() {
    }

    public String getToken() {
      return token;
    }

    public void setToken(String token) {
      this.token = token;
    }

    public String getFullName() {
      return fullName;
    }

    public void setFullName(String fullName) {
      this.fullName = fullName;
    }

    public String getPassword() {
      return password;
    }

    public void setPassword(String password) {
      this.password = password;
    }
  }

  public static class InviteResponse {
    private UUID id;
    private String email;
    private Role role;
    private Instant expiresAt;
    private Instant acceptedAt;
    /** Plaintext token: populated only on creation, never stored or re-shown. */
    private String token;

    public InviteResponse() {
    }

    public UUID getId() {
      return id;
    }

    public void setId(UUID id) {
      this.id = id;
    }

    public String getEmail() {
      return email;
    }

    public void setEmail(String email) {
      this.email = email;
    }

    public Role getRole() {
      return role;
    }

    public void setRole(Role role) {
      this.role = role;
    }

    public Instant getExpiresAt() {
      return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
      this.expiresAt = expiresAt;
    }

    public Instant getAcceptedAt() {
      return acceptedAt;
    }

    public void setAcceptedAt(Instant acceptedAt) {
      this.acceptedAt = acceptedAt;
    }

    public String getToken() {
      return token;
    }

    public void setToken(String token) {
      this.token = token;
    }
  }
}
