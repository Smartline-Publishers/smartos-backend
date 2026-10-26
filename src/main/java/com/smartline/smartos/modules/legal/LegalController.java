package com.smartline.smartos.modules.legal;

import com.smartline.smartos.common.ApiResponse;
import com.smartline.smartos.common.PageResponse;
import com.smartline.smartos.common.Paging;
import com.smartline.smartos.common.exception.ApiException;
import com.smartline.smartos.modules.legal.LegalDtos.LegalAcceptanceResponse;
import com.smartline.smartos.modules.legal.LegalDtos.LegalDocumentResponse;
import com.smartline.smartos.security.UserPrincipal;
import com.smartline.smartos.tenant.TenantContext;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/legal")
public class LegalController {

  private final LegalService service;

  public LegalController(LegalService service) {
    this.service = service;
  }

  /** Public: sign-up and invite pages must show documents before login. */
  @GetMapping("/documents")
  public ApiResponse<List<LegalDocumentResponse>> documents() {
    return ApiResponse.ok(service.currentDocuments());
  }

  /** The caller's own acceptance history, including content hashes. */
  @GetMapping("/acceptances/me")
  @PreAuthorize("isAuthenticated()")
  public ApiResponse<List<LegalAcceptanceResponse>> mine() {
    return ApiResponse.ok(service.acceptancesForUser(currentUserId()));
  }

  /** Organisation-wide evidence for administrators with audit access. */
  @GetMapping("/acceptances")
  @PreAuthorize("hasAuthority('PERM_audit.read')")
  public ApiResponse<PageResponse<LegalAcceptanceResponse>> organization(Pageable pageable) {
    UUID orgId = TenantContext.require();
    return ApiResponse.ok(service.acceptancesForOrganization(orgId, Paging.capped(pageable)));
  }

  private UUID currentUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
      return principal.getUserId();
    }
    throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
  }
}
