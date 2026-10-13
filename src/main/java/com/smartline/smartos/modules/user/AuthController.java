package com.smartline.smartos.modules.user;

import com.smartline.smartos.common.ApiResponse;
import com.smartline.smartos.security.AuthDtos.LoginRequest;
import com.smartline.smartos.security.AuthDtos.RefreshRequest;
import com.smartline.smartos.security.AuthDtos.RegisterOrgRequest;
import com.smartline.smartos.security.AuthDtos.TokenResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/login")
  public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest req) {
    return ApiResponse.ok(authService.login(req));
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
}
