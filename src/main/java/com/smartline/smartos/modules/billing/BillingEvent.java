package com.smartline.smartos.modules.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Webhook idempotency record: provider event ids are the natural key, so a
 * retried delivery is acknowledged without reprocessing.
 */
@Entity
@Table(name = "billing_events")
public class BillingEvent {

  @Id
  @Column(name = "provider_event_id")
  private String providerEventId;

  @Column(nullable = false, length = 128)
  private String type;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private Map<String, Object> payload;

  @Column(name = "received_at", nullable = false, updatable = false)
  private Instant receivedAt;

  public BillingEvent() {
    this.receivedAt = Instant.now();
  }

  public BillingEvent(String providerEventId, String type, Map<String, Object> payload) {
    this.providerEventId = providerEventId;
    this.type = type;
    this.payload = payload;
    this.receivedAt = Instant.now();
  }

  public String getProviderEventId() {
    return providerEventId;
  }

  public void setProviderEventId(String providerEventId) {
    this.providerEventId = providerEventId;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public Map<String, Object> getPayload() {
    return payload;
  }

  public void setPayload(Map<String, Object> payload) {
    this.payload = payload;
  }

  public Instant getReceivedAt() {
    return receivedAt;
  }

  public void setReceivedAt(Instant receivedAt) {
    this.receivedAt = receivedAt;
  }
}
