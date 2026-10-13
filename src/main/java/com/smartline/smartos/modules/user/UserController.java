package com.smartline.smartos.modules.user;

import com.smartline.smartos.common.ApiResponse;
import com.smartline.smartos.common.PageResponse;
import com.smartline.smartos.modules.user.UserDtos.CreateUserRequest;
import com.smartline.smartos.modules.user.UserDtos.UpdateUserRequest;
import com.smartline.smartos.modules.user.UserDtos.UserResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

  private final UserService service;

  public UserController(UserService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ORG_ADMIN','PLATFORM_ADMIN')")
  public ApiResponse<PageResponse<UserResponse>> list(Pageable pageable) {
    return ApiResponse.ok(PageResponse.from(service.list(pageable).map(UserResponse::from)));
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
}
