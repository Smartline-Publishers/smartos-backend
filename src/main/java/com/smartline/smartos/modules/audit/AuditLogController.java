package com.smartline.smartos.modules.audit;

import com.smartline.smartos.common.ApiResponse;
import com.smartline.smartos.common.PageResponse;
import com.smartline.smartos.common.Paging;
import com.smartline.smartos.modules.audit.AuditLogDtos.AuditLogResponse;
import com.smartline.smartos.modules.user.AppUserRepository;
import com.smartline.smartos.tenant.TenantContext;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditLogController {

  private final AuditService service;
  private final AppUserRepository users;

  public AuditLogController(AuditService service, AppUserRepository users) {
    this.service = service;
    this.users = users;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ORG_ADMIN','PLATFORM_ADMIN')")
  public ApiResponse<PageResponse<AuditLogResponse>> list(Pageable pageable) {
    UUID orgId = TenantContext.require();
    Map<UUID, String> names = new HashMap<>();
    users.findByOrganizationId(orgId, Paging.capped(pageable)).getContent()
        .forEach(u -> names.put(u.getId(), u.getFullName()));
    return ApiResponse.ok(PageResponse.from(
        service.list(Paging.capped(pageable)).map(log -> toResponse(log,
            log.getActorId() != null ? names.getOrDefault(log.getActorId(), "?") : null))));
  }

  private AuditLogResponse toResponse(AuditLog log, String actorName) {
    return new AuditLogResponse(log.getId(), log.getActorId(), actorName, log.getAction(),
        log.getEntityType(), log.getEntityId(), log.getCreatedAt());
  }
}
