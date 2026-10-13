package com.smartline.smartos.security;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public final class AuthDtos {

  private AuthDtos() {
  }

  public static class LoginRequest {
    @Email @NotBlank private String email;
    @NotBlank private String password;
    private UUID organizationId;

    public LoginRequest() {
    }

    public LoginRequest(String email, String password, UUID organizationId) {
      this.email = email;
      this.password = password;
      this.organizationId = organizationId;
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

    public UUID getOrganizationId() {
      return organizationId;
    }

    public void setOrganizationId(UUID organizationId) {
      this.organizationId = organizationId;
    }
  }

  public static class TokenResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType = "Bearer";

    public TokenResponse() {
    }

    public TokenResponse(String accessToken, String refreshToken, String tokenType) {
      this.accessToken = accessToken;
      this.refreshToken = refreshToken;
      this.tokenType = tokenType;
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

    public String getTokenType() {
      return tokenType;
    }

    public void setTokenType(String tokenType) {
      this.tokenType = tokenType;
    }
  }

  public static class RefreshRequest {
    @NotBlank private String refreshToken;

    public RefreshRequest() {
    }

    public RefreshRequest(String refreshToken) {
      this.refreshToken = refreshToken;
    }

    public String getRefreshToken() {
      return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
      this.refreshToken = refreshToken;
    }
  }

  public static class RegisterOrgRequest {
    @NotBlank private String organizationName;
    @NotBlank private String slug;
    @Email @NotBlank private String adminEmail;
    @NotBlank private String adminPassword;
    @NotBlank private String adminFullName;

    public RegisterOrgRequest() {
    }

    public RegisterOrgRequest(String organizationName, String slug, String adminEmail,
        String adminPassword, String adminFullName) {
      this.organizationName = organizationName;
      this.slug = slug;
      this.adminEmail = adminEmail;
      this.adminPassword = adminPassword;
      this.adminFullName = adminFullName;
    }

    public String getOrganizationName() {
      return organizationName;
    }

    public void setOrganizationName(String organizationName) {
      this.organizationName = organizationName;
    }

    public String getSlug() {
      return slug;
    }

    public void setSlug(String slug) {
      this.slug = slug;
    }

    public String getAdminEmail() {
      return adminEmail;
    }

    public void setAdminEmail(String adminEmail) {
      this.adminEmail = adminEmail;
    }

    public String getAdminPassword() {
      return adminPassword;
    }

    public void setAdminPassword(String adminPassword) {
      this.adminPassword = adminPassword;
    }

    public String getAdminFullName() {
      return adminFullName;
    }

    public void setAdminFullName(String adminFullName) {
      this.adminFullName = adminFullName;
    }
  }
}
