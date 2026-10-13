package com.smartline.smartos.modules.task;

import com.smartline.smartos.modules.task.Task.TaskPriority;
import com.smartline.smartos.modules.task.Task.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class TaskDtos {

  private TaskDtos() {
  }

  public static class CreateTaskRequest {
    @NotBlank private String title;
    @Size(max = 5000) private String description;
    private TaskStatus status;
    private TaskPriority priority;
    private UUID assigneeId;
    private UUID bookId;
    private UUID manuscriptId;
    private LocalDate dueDate;

    public CreateTaskRequest() {
    }

    public String getTitle() {
      return title;
    }

    public void setTitle(String title) {
      this.title = title;
    }

    public String getDescription() {
      return description;
    }

    public void setDescription(String description) {
      this.description = description;
    }

    public TaskStatus getStatus() {
      return status;
    }

    public void setStatus(TaskStatus status) {
      this.status = status;
    }

    public TaskPriority getPriority() {
      return priority;
    }

    public void setPriority(TaskPriority priority) {
      this.priority = priority;
    }

    public UUID getAssigneeId() {
      return assigneeId;
    }

    public void setAssigneeId(UUID assigneeId) {
      this.assigneeId = assigneeId;
    }

    public UUID getBookId() {
      return bookId;
    }

    public void setBookId(UUID bookId) {
      this.bookId = bookId;
    }

    public UUID getManuscriptId() {
      return manuscriptId;
    }

    public void setManuscriptId(UUID manuscriptId) {
      this.manuscriptId = manuscriptId;
    }

    public LocalDate getDueDate() {
      return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
      this.dueDate = dueDate;
    }
  }

  public static class UpdateTaskRequest {
    private String title;
    @Size(max = 5000) private String description;
    private TaskStatus status;
    private TaskPriority priority;
    private UUID assigneeId;
    private UUID bookId;
    private UUID manuscriptId;
    private LocalDate dueDate;

    public UpdateTaskRequest() {
    }

    public String getTitle() {
      return title;
    }

    public void setTitle(String title) {
      this.title = title;
    }

    public String getDescription() {
      return description;
    }

    public void setDescription(String description) {
      this.description = description;
    }

    public TaskStatus getStatus() {
      return status;
    }

    public void setStatus(TaskStatus status) {
      this.status = status;
    }

    public TaskPriority getPriority() {
      return priority;
    }

    public void setPriority(TaskPriority priority) {
      this.priority = priority;
    }

    public UUID getAssigneeId() {
      return assigneeId;
    }

    public void setAssigneeId(UUID assigneeId) {
      this.assigneeId = assigneeId;
    }

    public UUID getBookId() {
      return bookId;
    }

    public void setBookId(UUID bookId) {
      this.bookId = bookId;
    }

    public UUID getManuscriptId() {
      return manuscriptId;
    }

    public void setManuscriptId(UUID manuscriptId) {
      this.manuscriptId = manuscriptId;
    }

    public LocalDate getDueDate() {
      return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
      this.dueDate = dueDate;
    }
  }

  public static class TaskResponse {
    private UUID id;
    private UUID organizationId;
    private String title;
    private String description;
    private TaskStatus status;
    private TaskPriority priority;
    private UUID assigneeId;
    private UUID bookId;
    private UUID manuscriptId;
    private LocalDate dueDate;
    private Instant createdAt;
    private Instant updatedAt;

    public TaskResponse() {
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

    public String getTitle() {
      return title;
    }

    public void setTitle(String title) {
      this.title = title;
    }

    public String getDescription() {
      return description;
    }

    public void setDescription(String description) {
      this.description = description;
    }

    public TaskStatus getStatus() {
      return status;
    }

    public void setStatus(TaskStatus status) {
      this.status = status;
    }

    public TaskPriority getPriority() {
      return priority;
    }

    public void setPriority(TaskPriority priority) {
      this.priority = priority;
    }

    public UUID getAssigneeId() {
      return assigneeId;
    }

    public void setAssigneeId(UUID assigneeId) {
      this.assigneeId = assigneeId;
    }

    public UUID getBookId() {
      return bookId;
    }

    public void setBookId(UUID bookId) {
      this.bookId = bookId;
    }

    public UUID getManuscriptId() {
      return manuscriptId;
    }

    public void setManuscriptId(UUID manuscriptId) {
      this.manuscriptId = manuscriptId;
    }

    public LocalDate getDueDate() {
      return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
      this.dueDate = dueDate;
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
  }

  public static class TaskMapper {
    private TaskMapper() {
    }

    public static TaskResponse toResponse(Task t) {
      UUID orgId = t.getOrganization() != null ? t.getOrganization().getId() : t.getOrganizationId();
      TaskResponse r = new TaskResponse();
      r.setId(t.getId());
      r.setOrganizationId(orgId);
      r.setTitle(t.getTitle());
      r.setDescription(t.getDescription());
      r.setStatus(t.getStatus());
      r.setPriority(t.getPriority());
      r.setAssigneeId(t.getAssignee() != null ? t.getAssignee().getId() : null);
      r.setBookId(t.getBook() != null ? t.getBook().getId() : null);
      r.setManuscriptId(t.getManuscript() != null ? t.getManuscript().getId() : null);
      r.setDueDate(t.getDueDate());
      r.setCreatedAt(t.getCreatedAt());
      r.setUpdatedAt(t.getUpdatedAt());
      return r;
    }
  }
}
