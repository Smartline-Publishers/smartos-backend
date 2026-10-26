package com.smartline.smartos.modules.user;

import com.smartline.smartos.common.ApiResponse;
import com.smartline.smartos.modules.user.PasswordResetDtos.ForgotRequest;
import com.smartline.smartos.modules.user.PasswordResetDtos.ForgotResponse;
import com.smartline.smartos.modules.user.PasswordResetDtos.ResetRequest;
import com.smartline.smartos.modules.user.PasswordResetDtos.ValidateRequest;
import com.smartline.smartos.modules.user.PasswordResetDtos.ValidateResponse;
import com.smartline.smartos.modules.user.PasskeyDtos.AssertionRequest;
import com.smartline.smartos.modules.user.PasskeyDtos.AuthenticationOptionsRequest;
import com.smartline.smartos.security.AuthDtos.LoginRequest;
import com.smartline.smartos.security.AuthDtos.RefreshRequest;
import com.smartline.smartos.security.AuthDtos.RegisterOrgRequest;
import com.smartline.smartos.security.AuthDtos.SessionInfo;
import com.smartline.smartos.security.AuthDtos.TokenResponse;
import com.smartline.smartos.security.AuthDtos.TwoFactorVerifyRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthService authService;
  private final PasswordResetService passwordResetService;
  private final WebAuthnService webAuthnService;
  private final boolean trustForwardedHeaders;

  public AuthController(AuthService authService, PasswordResetService passwordResetService,
      WebAuthnService webAuthnService,
      @Value("${app.security.trust-forwarded-headers:false}") boolean trustForwardedHeaders) {
    this.authService = authService;
    this.passwordResetService = passwordResetService;
    this.webAuthnService = webAuthnService;
    this.trustForwardedHeaders = trustForwardedHeaders;
  }

  @PostMapping("/login")
  public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest req,
      HttpServletRequest request) {
    return ApiResponse.ok(authService.login(req, clientIp(request)));
  }

  @PostMapping("/2fa/verify")
  public ApiResponse<TokenResponse> verifyTwoFactor(
      @Valid @RequestBody TwoFactorVerifyRequest req) {
    return ApiResponse.ok(authService.verifyTwoFactor(req));
  }

  @PostMapping("/refresh")
  public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest req) {
    return ApiResponse.ok(authService.refresh(req.getRefreshToken()));
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(@Valid @RequestBody RefreshRequest req) {
    authService.logout(req.getRefreshToken());
  }

  @PostMapping("/register-organization")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<TokenResponse> registerOrganization(
      @Valid @RequestBody RegisterOrgRequest req) {
    // Bootstraps the organization + its first ORG_ADMIN and returns tokens,
    // so the new admin is signed in immediately.
    return ApiResponse.ok(authService.bootstrapOrganization(req));
  }

  // ---- Password recovery ----

  /**
   * Always answers the same generic message so an attacker cannot use this
   * endpoint to discover which emails have accounts.
   */
  @PostMapping("/password/forgot")
  public ApiResponse<ForgotResponse> forgotPassword(@Valid @RequestBody ForgotRequest req,
      HttpServletRequest request) {
    return ApiResponse.ok(passwordResetService.requestReset(req.getEmail(), clientIp(request)));
  }

  @PostMapping("/password/reset/validate")
  public ApiResponse<ValidateResponse> validateResetToken(
      @Valid @RequestBody ValidateRequest req) {
    passwordResetService.validate(req.getToken());
    return ApiResponse.ok(new ValidateResponse(true));
  }

  @PostMapping("/password/reset")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void resetPassword(@Valid @RequestBody ResetRequest req) {
    passwordResetService.reset(req.getToken(), req.getNewPassword(), req.getConfirmPassword());
  }

  // ---- Passkey sign-in ----

  @PostMapping("/passkeys/options")
  public ApiResponse<java.util.Map<String, Object>> passkeyOptions(
      @RequestBody(required = false) AuthenticationOptionsRequest req) {
    return ApiResponse.ok(webAuthnService.authenticationOptions(req == null ? null : req.getEmail()));
  }

  @PostMapping("/passkeys/verify")
  public ApiResponse<TokenResponse> verifyPasskey(@Valid @RequestBody AssertionRequest req,
      HttpServletRequest request) {
    return ApiResponse.ok(webAuthnService.authenticate(req, clientIp(request)));
  }

  /**
   * The caller's effective access: live role, permissions, and assignable
   * roles. The web app uses this to render navigation, dashboard widgets and
   * actions, so the UI can never offer something the API would reject.
   */
  @GetMapping("/me")
  @PreAuthorize("isAuthenticated()")
  public ApiResponse<SessionInfo> me() {
    return ApiResponse.ok(authService.sessionInfo());
  }

  private String clientIp(HttpServletRequest request) {
    if (trustForwardedHeaders) {
      String forwarded = request.getHeader("X-Forwarded-For");
      if (forwarded != null && !forwarded.isBlank()) {
        // First entry is the original client; only trusted when the app is
        // explicitly configured behind a proxy that overwrites this header.
        return forwarded.split(",")[0].trim();
      }
    }
    return request.getRemoteAddr();
  }
}
