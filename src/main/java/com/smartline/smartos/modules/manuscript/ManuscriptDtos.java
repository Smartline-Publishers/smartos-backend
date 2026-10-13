package com.smartline.smartos.modules.manuscript;

import com.smartline.smartos.modules.manuscript.Manuscript.ManuscriptStage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ManuscriptDtos {

  private ManuscriptDtos() {
  }

  public static class CreateManuscriptRequest {
    @NotBlank private String title;
    private UUID bookId;
    private UUID contributorId;
    private UUID assigneeId;
    private LocalDate dueDate;
    @Size(max = 1024) private String fileUrl;

    public CreateManuscriptRequest() {
    }

    public String getTitle() {
      return title;
    }

    public void setTitle(String title) {
      this.title = title;
    }

    public UUID getBookId() {
      return bookId;
    }

    public void setBookId(UUID bookId) {
      this.bookId = bookId;
    }

    public UUID getContributorId() {
      return contributorId;
    }

    public void setContributorId(UUID contributorId) {
      this.contributorId = contributorId;
    }

    public UUID getAssigneeId() {
      return assigneeId;
    }

    public void setAssigneeId(UUID assigneeId) {
      this.assigneeId = assigneeId;
    }

    public LocalDate getDueDate() {
      return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
      this.dueDate = dueDate;
    }

    public String getFileUrl() {
      return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
      this.fileUrl = fileUrl;
    }
  }

  public static class UpdateManuscriptRequest {
    private String title;
    private UUID bookId;
    private UUID contributorId;
    private UUID assigneeId;
    private LocalDate dueDate;
    @Size(max = 1024) private String fileUrl;

    public UpdateManuscriptRequest() {
    }

    public String getTitle() {
      return title;
    }

    public void setTitle(String title) {
      this.title = title;
    }

    public UUID getBookId() {
      return bookId;
    }

    public void setBookId(UUID bookId) {
      this.bookId = bookId;
    }

    public UUID getContributorId() {
      return contributorId;
    }

    public void setContributorId(UUID contributorId) {
      this.contributorId = contributorId;
    }

    public UUID getAssigneeId() {
      return assigneeId;
    }

    public void setAssigneeId(UUID assigneeId) {
      this.assigneeId = assigneeId;
    }

    public LocalDate getDueDate() {
      return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
      this.dueDate = dueDate;
    }

    public String getFileUrl() {
      return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
      this.fileUrl = fileUrl;
    }
  }

  public static class TransitionManuscriptRequest {
    @NotNull private ManuscriptStage toStage;
    @Size(max = 2000) private String comment;

    public TransitionManuscriptRequest() {
    }

    public ManuscriptStage getToStage() {
      return toStage;
    }

    public void setToStage(ManuscriptStage toStage) {
      this.toStage = toStage;
    }

    public String getComment() {
      return comment;
    }

    public void setComment(String comment) {
      this.comment = comment;
    }
  }

  public static class ManuscriptEventResponse {
    private UUID id;
    private ManuscriptStage fromStage;
    private ManuscriptStage toStage;
    private UUID actorId;
    private String comment;
    private Instant createdAt;

    public ManuscriptEventResponse() {
    }

    public ManuscriptEventResponse(UUID id, ManuscriptStage fromStage, ManuscriptStage toStage,
        UUID actorId, String comment, Instant createdAt) {
      this.id = id;
      this.fromStage = fromStage;
      this.toStage = toStage;
      this.actorId = actorId;
      this.comment = comment;
      this.createdAt = createdAt;
    }

    public UUID getId() {
      return id;
    }

    public void setId(UUID id) {
      this.id = id;
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

  public static class ManuscriptResponse {
    private UUID id;
    private UUID organizationId;
    private UUID bookId;
    private String title;
    private UUID contributorId;
    private ManuscriptStage stage;
    private UUID assigneeId;
    private LocalDate dueDate;
    private int version;
    private String fileUrl;
    private Instant createdAt;
    private Instant updatedAt;
    private List<ManuscriptEventResponse> events;

    public ManuscriptResponse() {
    }

    public UUID getId() {
      return id;
    }

    public void setId(UUID id) {
      this.id = id;
    }

    public UUID getOrganizationId() {
      return organizationId;
    }

    public void setOrganizationId(UUID organizationId) {
      this.organizationId = organizationId;
    }

    public UUID getBookId() {
      return bookId;
    }

    public void setBookId(UUID bookId) {
      this.bookId = bookId;
    }

    public String getTitle() {
      return title;
    }

    public void setTitle(String title) {
      this.title = title;
    }

    public UUID getContributorId() {
      return contributorId;
    }

    public void setContributorId(UUID contributorId) {
      this.contributorId = contributorId;
    }

    public ManuscriptStage getStage() {
      return stage;
    }

    public void setStage(ManuscriptStage stage) {
      this.stage = stage;
    }

    public UUID getAssigneeId() {
      return assigneeId;
    }

    public void setAssigneeId(UUID assigneeId) {
      this.assigneeId = assigneeId;
    }

    public LocalDate getDueDate() {
      return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
      this.dueDate = dueDate;
    }

    public int getVersion() {
      return version;
    }

    public void setVersion(int version) {
      this.version = version;
    }

    public String getFileUrl() {
      return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
      this.fileUrl = fileUrl;
    }

    public Instant getCreatedAt() {
      return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
      this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
      return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
      this.updatedAt = updatedAt;
    }

    public List<ManuscriptEventResponse> getEvents() {
      return events;
    }

    public void setEvents(List<ManuscriptEventResponse> events) {
      this.events = events;
    }
  }

  public static class ManuscriptMapper {
    private ManuscriptMapper() {
    }

    public static ManuscriptEventResponse toResponse(ManuscriptEvent e) {
      return new ManuscriptEventResponse(e.getId(), e.getFromStage(), e.getToStage(),
          e.getActorId(), e.getComment(), e.getCreatedAt());
    }

    public static ManuscriptResponse toResponse(Manuscript m, List<ManuscriptEventResponse> events) {
      UUID orgId = m.getOrganization() != null ? m.getOrganization().getId() : m.getOrganizationId();
      ManuscriptResponse r = new ManuscriptResponse();
      r.setId(m.getId());
      r.setOrganizationId(orgId);
      r.setBookId(m.getBook() != null ? m.getBook().getId() : null);
      r.setTitle(m.getTitle());
      r.setContributorId(m.getContributor() != null ? m.getContributor().getId() : null);
      r.setStage(m.getStage());
      r.setAssigneeId(m.getAssignee() != null ? m.getAssignee().getId() : null);
      r.setDueDate(m.getDueDate());
      r.setVersion(m.getVersion());
      r.setFileUrl(m.getFileUrl());
      r.setCreatedAt(m.getCreatedAt());
      r.setUpdatedAt(m.getUpdatedAt());
      r.setEvents(events != null ? events : List.of());
      return r;
    }
  }
}
