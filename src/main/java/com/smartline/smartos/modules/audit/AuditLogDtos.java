package com.smartline.smartos.modules.audit;

import java.time.Instant;
import java.util.UUID;

public final class AuditLogDtos {

  private AuditLogDtos() {
  }

  public static class AuditLogResponse {
    private UUID id;
    private UUID actorId;
    private String actorName;
    private String action;
    private String entityType;
    private String entityId;
    private Instant createdAt;

    public AuditLogResponse() {
    }

    public AuditLogResponse(UUID id, UUID actorId, String actorName, String action,
        String entityType, String entityId, Instant createdAt) {
      this.id = id;
      this.actorId = actorId;
      this.actorName = actorName;
      this.action = action;
      this.entityType = entityType;
      this.entityId = entityId;
      this.createdAt = createdAt;
    }

    public UUID getId() {
      return id;
    }

    public void setId(UUID id) {
      this.id = id;
    }

    public UUID getActorId() {
      return actorId;
    }

    public void setActorId(UUID actorId) {
      this.actorId = actorId;
    }

    public String getActorName() {
      return actorName;
    }

    public void setActorName(String actorName) {
      this.actorName = actorName;
    }

    public String getAction() {
      return action;
    }

    public void setAction(String action) {
      this.action = action;
    }

    public String getEntityType() {
      return entityType;
    }

    public void setEntityType(String entityType) {
      this.entityType = entityType;
    }

    public String getEntityId() {
      return entityId;
    }

    public void setEntityId(String entityId) {
      this.entityId = entityId;
    }

    public Instant getCreatedAt() {
      return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
      this.createdAt = createdAt;
    }
  }
}
