package com.smartline.smartos.modules.audit;

import com.smartline.smartos.security.UserPrincipal;
import com.smartline.smartos.tenant.TenantContext;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

  private static final Logger log = LoggerFactory.getLogger(AuditService.class);

  private final AuditLogRepository repository;

  public AuditService(AuditLogRepository repository) {
    this.repository = repository;
  }

  public void log(String action, String entityType, String entityId, Map<String, Object> metadata) {
    try {
      AuditLog entry = new AuditLog();
      entry.setOrganizationId(TenantContext.current());
      entry.setActorId(currentActorId());
      entry.setAction(action);
      entry.setEntityType(entityType);
      entry.setEntityId(entityId);
      entry.setMetadata(metadata);
      repository.save(entry);
    } catch (Exception e) {
      log.warn("Failed to persist audit log action={}", action, e);
    }
  }

  private UUID currentActorId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
      return principal.getUserId();
    }
    return null;
  }
}
