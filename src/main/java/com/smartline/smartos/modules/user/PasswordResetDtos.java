package com.smartline.smartos.modules.user;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class PasswordResetDtos {

  private PasswordResetDtos() {
  }

  public static class ForgotRequest {
    @Email @NotBlank private String email;

    public ForgotRequest() {
    }

    public String getEmail() {
      return email;
    }

    public void setEmail(String email) {
      this.email = email;
    }
  }

  public static class ResetRequest {
    @NotBlank private String token;
    @NotBlank @Size(min = 10, message = "Password must be at least 10 characters")
    private String newPassword;
    @NotBlank private String confirmPassword;

    public ResetRequest() {
    }

    public String getToken() {
      return token;
    }

    public void setToken(String token) {
      this.token = token;
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
  }

  public static class ValidateRequest {
    @NotBlank private String token;

    public ValidateRequest() {
    }

    public String getToken() {
      return token;
    }

    public void setToken(String token) {
      this.token = token;
    }
  }

  public static class ForgotResponse {
    /** Always true for a syntactically valid request (anti-enumeration). */
    private boolean sent = true;

    /**
     * Only populated in non-production when
     * app.security.password-reset.expose-dev-token is explicitly enabled.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String devToken;

    public ForgotResponse() {
    }

    public ForgotResponse(boolean sent, String devToken) {
      this.sent = sent;
      this.devToken = devToken;
    }

    public boolean isSent() {
      return sent;
    }

    public void setSent(boolean sent) {
      this.sent = sent;
    }

    public String getDevToken() {
      return devToken;
    }

    public void setDevToken(String devToken) {
      this.devToken = devToken;
    }
  }

  public static class ValidateResponse {
    private boolean valid;

    public ValidateResponse() {
    }

    public ValidateResponse(boolean valid) {
      this.valid = valid;
    }

    public boolean isValid() {
      return valid;
    }

    public void setValid(boolean valid) {
      this.valid = valid;
    }
  }
}
