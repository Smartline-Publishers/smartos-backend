package com.smartline.smartos.modules.user;

import com.smartline.smartos.common.ApiResponse;
import com.smartline.smartos.modules.user.InviteDtos.AcceptInviteRequest;
import com.smartline.smartos.modules.user.InviteDtos.CreateInviteRequest;
import com.smartline.smartos.modules.user.InviteDtos.InviteResponse;
import com.smartline.smartos.security.AuthDtos.TokenResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/invites")
public class InviteController {

  private final InviteService service;

  public InviteController(InviteService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_invites.manage')")
  public ApiResponse<List<InviteResponse>> pending() {
    return ApiResponse.ok(service.pending());
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('PERM_invites.manage')")
  public ApiResponse<InviteResponse> create(@Valid @RequestBody CreateInviteRequest req) {
    return ApiResponse.ok(service.create(req));
  }

  @PostMapping("/accept")
  @PreAuthorize("permitAll()")
  public ApiResponse<TokenResponse> accept(@Valid @RequestBody AcceptInviteRequest req) {
    return ApiResponse.ok(service.accept(req));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('PERM_invites.manage')")
  public void revoke(@PathVariable UUID id) {
    service.revoke(id);
  }
}
