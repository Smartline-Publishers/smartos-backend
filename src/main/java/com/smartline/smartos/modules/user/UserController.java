package com.smartline.smartos.modules.user;

import com.smartline.smartos.common.ApiResponse;
import com.smartline.smartos.common.Paging;
import com.smartline.smartos.common.PageResponse;
import com.smartline.smartos.modules.user.UserDtos.ChangePasswordRequest;
import com.smartline.smartos.modules.user.UserDtos.CreateUserRequest;
import com.smartline.smartos.modules.user.UserDtos.SessionResponse;
import com.smartline.smartos.modules.user.UserDtos.UpdateProfileRequest;
import com.smartline.smartos.modules.user.UserDtos.UpdateUserRequest;
import com.smartline.smartos.modules.user.UserDtos.UserResponse;
import com.smartline.smartos.security.UserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

  private final UserService service;
  private final TwoFactorService twoFactor;

  public UserController(UserService service, TwoFactorService twoFactor) {
    this.service = service;
    this.twoFactor = twoFactor;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ORG_ADMIN','PLATFORM_ADMIN')")
  public ApiResponse<PageResponse<UserResponse>> list(Pageable pageable) {
    return ApiResponse.ok(PageResponse.from(service.list(Paging.capped(pageable)).map(UserResponse::from)));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN','PLATFORM_ADMIN')")
  public ApiResponse<UserResponse> get(@PathVariable UUID id) {
    return ApiResponse.ok(UserResponse.from(service.get(id)));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('ORG_ADMIN','PLATFORM_ADMIN')")
  public ApiResponse<UserResponse> create(@Valid @RequestBody CreateUserRequest req) {
    return ApiResponse.ok(UserResponse.from(service.create(req)));
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN','PLATFORM_ADMIN')")
  public ApiResponse<UserResponse> update(@PathVariable UUID id,
      @RequestBody UpdateUserRequest req) {
    return ApiResponse.ok(UserResponse.from(service.update(id, req)));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAnyRole('ORG_ADMIN','PLATFORM_ADMIN')")
  public void delete(@PathVariable UUID id) {
    service.delete(id);
  }

  // ---- Self-service profile ----

  @GetMapping("/me")
  @PreAuthorize("isAuthenticated()")
  public ApiResponse<UserResponse> me() {
    return ApiResponse.ok(UserResponse.from(service.me()));
  }

  @PatchMapping("/me")
  @PreAuthorize("isAuthenticated()")
  public ApiResponse<UserResponse> updateProfile(@RequestBody UpdateProfileRequest req) {
    return ApiResponse.ok(UserResponse.from(service.updateProfile(req)));
  }

  @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("isAuthenticated()")
  public ApiResponse<UserResponse> uploadAvatar(@RequestParam("file") MultipartFile file) {
    return ApiResponse.ok(UserResponse.from(service.uploadAvatar(file)));
  }

  @PostMapping("/me/password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("isAuthenticated()")
  public void changePassword(@Valid @RequestBody ChangePasswordRequest req) {
    service.changePassword(req);
  }

  @GetMapping("/me/sessions")
  @PreAuthorize("isAuthenticated()")
  public ApiResponse<List<SessionResponse>> sessions(
      @RequestHeader(value = "X-Refresh-Token", required = false) String refreshToken) {
    return ApiResponse.ok(service.sessions(refreshToken));
  }

  @DeleteMapping("/me/sessions/{sessionId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("isAuthenticated()")
  public void revokeSession(@PathVariable UUID sessionId,
      @RequestHeader(value = "X-Refresh-Token", required = false) String refreshToken) {
    service.revokeSession(sessionId, refreshToken);
  }

  // ---- Self-service two-factor authentication ----

  @PostMapping("/me/2fa/setup")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("isAuthenticated()")
  public ApiResponse<TwoFactorService.SetupResponse> beginTwoFactorSetup() {
    return ApiResponse.ok(twoFactor.beginSetup(currentUserId()));
  }

  @PostMapping("/me/2fa/verify")
  @PreAuthorize("isAuthenticated()")
  public ApiResponse<TwoFactorService.ConfirmResponse> confirmTwoFactorSetup(
      @Valid @RequestBody TwoFactorCodeRequest req) {
    return ApiResponse.ok(twoFactor.confirmSetup(currentUserId(), req.getCode()));
  }

  @GetMapping("/me/2fa/status")
  @PreAuthorize("isAuthenticated()")
  public ApiResponse<TwoFactorService.StatusResponse> twoFactorStatus() {
    return ApiResponse.ok(twoFactor.status(currentUserId()));
  }

  @PostMapping("/me/2fa/disable")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("isAuthenticated()")
  public void disableTwoFactor(@Valid @RequestBody TwoFactorDisableRequest req) {
    twoFactor.disable(currentUserId(), req.getCurrentPassword());
  }

  private UUID currentUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
      return principal.getUserId();
    }
    throw new com.smartline.smartos.common.exception.ApiException(HttpStatus.UNAUTHORIZED,
        "UNAUTHORIZED", "Authentication required");
  }

  public static class TwoFactorCodeRequest {
    @jakarta.validation.constraints.NotBlank private String code;

    public TwoFactorCodeRequest() {
    }

    public String getCode() {
      return code;
    }

    public void setCode(String code) {
      this.code = code;
    }
  }

  public static class TwoFactorDisableRequest {
    @jakarta.validation.constraints.NotBlank private String currentPassword;

    public TwoFactorDisableRequest() {
    }

    public String getCurrentPassword() {
      return currentPassword;
    }

    public void setCurrentPassword(String currentPassword) {
      this.currentPassword = currentPassword;
    }
  }
}
