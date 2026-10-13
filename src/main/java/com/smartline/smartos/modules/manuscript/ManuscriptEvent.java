package com.smartline.smartos.modules.manuscript;

import com.smartline.smartos.modules.manuscript.Manuscript.ManuscriptStage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Immutable-by-convention record of a manuscript stage transition
 * (submission, review decision, approval, ...). No update endpoint.
 */
@Entity
@Table(name = "manuscript_events")
public class ManuscriptEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "manuscript_id", nullable = false)
  private Manuscript manuscript;

  @Enumerated(EnumType.STRING)
  @Column(name = "from_stage")
  private ManuscriptStage fromStage;

  @Enumerated(EnumType.STRING)
  @Column(name = "to_stage", nullable = false)
  private ManuscriptStage toStage;

  @Column(name = "actor_id")
  private UUID actorId;

  @Column(columnDefinition = "TEXT")
  private String comment;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public ManuscriptEvent() {
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public Manuscript getManuscript() {
    return manuscript;
  }

  public void setManuscript(Manuscript manuscript) {
    this.manuscript = manuscript;
  }

  public ManuscriptStage getFromStage() {
    return fromStage;
  }

  public void setFromStage(ManuscriptStage fromStage) {
    this.fromStage = fromStage;
  }

  public ManuscriptStage getToStage() {
    return toStage;
  }

  public void setToStage(ManuscriptStage toStage) {
    this.toStage = toStage;
  }

  public UUID getActorId() {
    return actorId;
  }

  public void setActorId(UUID actorId) {
    this.actorId = actorId;
  }

  public String getComment() {
    return comment;
  }

  public void setComment(String comment) {
    this.comment = comment;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
