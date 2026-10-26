package com.smartline.smartos.modules.audit;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
  Page<AuditLog> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);
  List<AuditLog> findTop8ByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

  /**
   * A single actor's entries, always constrained to the organisation as well.
   * Activity is never looked up by actor alone: two organisations may contain
   * the same user id only in theory, but the tenant boundary is enforced here
   * so a caller can never reach another organisation's rows.
   */
  Page<AuditLog> findByOrganizationIdAndActorIdOrderByCreatedAtDesc(UUID organizationId,
      UUID actorId, Pageable pageable);

  List<AuditLog> findTop8ByOrganizationIdAndActorIdOrderByCreatedAtDesc(UUID organizationId,
      UUID actorId);
}
