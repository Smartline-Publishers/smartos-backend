package com.smartline.smartos.modules.task;

import com.smartline.smartos.common.exception.ApiException;
import com.smartline.smartos.modules.audit.Audited;
import com.smartline.smartos.modules.book.Book;
import com.smartline.smartos.modules.book.BookRepository;
import com.smartline.smartos.modules.manuscript.Manuscript;
import com.smartline.smartos.modules.manuscript.ManuscriptRepository;
import com.smartline.smartos.modules.organization.Organization;
import com.smartline.smartos.modules.organization.OrganizationRepository;
import com.smartline.smartos.modules.task.Task.TaskPriority;
import com.smartline.smartos.modules.task.Task.TaskStatus;
import com.smartline.smartos.modules.task.TaskDtos.CreateTaskRequest;
import com.smartline.smartos.modules.task.TaskDtos.UpdateTaskRequest;
import com.smartline.smartos.modules.user.AppUser;
import com.smartline.smartos.modules.user.AppUserRepository;
import com.smartline.smartos.tenant.TenantContext;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {

  private final TaskRepository tasks;
  private final OrganizationRepository organizations;
  private final AppUserRepository users;
  private final BookRepository books;
  private final ManuscriptRepository manuscripts;

  public TaskService(TaskRepository tasks, OrganizationRepository organizations,
      AppUserRepository users, BookRepository books, ManuscriptRepository manuscripts) {
    this.tasks = tasks;
    this.organizations = organizations;
    this.users = users;
    this.books = books;
    this.manuscripts = manuscripts;
  }

  @Transactional
  @Audited(action = "task.create", entityType = "Task")
  public Task create(CreateTaskRequest req) {
    UUID orgId = TenantContext.require();
    Organization org = organizations.findById(orgId)
        .orElseThrow(() -> ApiException.notFound("Organization", orgId));
    Task task = new Task();
    task.setOrganization(org);
    task.setTitle(req.getTitle().trim());
    task.setDescription(req.getDescription());
    task.setStatus(req.getStatus() != null ? req.getStatus() : TaskStatus.TODO);
    task.setPriority(req.getPriority() != null ? req.getPriority() : TaskPriority.MEDIUM);
    task.setAssignee(resolveAssignee(orgId, req.getAssigneeId()));
    task.setBook(resolveBook(orgId, req.getBookId()));
    task.setManuscript(resolveManuscript(orgId, req.getManuscriptId()));
    task.setDueDate(req.getDueDate());
    return tasks.save(task);
  }

  @Transactional(readOnly = true)
  public Page<Task> list(TaskStatus status, String search, UUID assigneeId, Pageable pageable) {
    UUID orgId = TenantContext.require();
    if (search != null && !search.isBlank()) {
      return tasks.findByOrganizationIdAndTitleContainingIgnoreCase(orgId, search.trim(), pageable);
    }
    if (status != null) {
      return tasks.findByOrganizationIdAndStatus(orgId, status, pageable);
    }
    if (assigneeId != null) {
      return tasks.findByOrganizationIdAndAssigneeId(orgId, assigneeId, pageable);
    }
    return tasks.findByOrganizationId(orgId, pageable);
  }

  @Transactional(readOnly = true)
  public Task get(UUID id) {
    UUID orgId = TenantContext.require();
    return tasks.findByIdAndOrganizationId(id, orgId)
        .orElseThrow(() -> ApiException.notFound("Task", id));
  }

  @Transactional
  @Audited(action = "task.update", entityType = "Task")
  public Task update(UUID id, UpdateTaskRequest req) {
    Task task = get(id);
    UUID orgId = TenantContext.require();
    if (req.getTitle() != null && !req.getTitle().isBlank()) {
      task.setTitle(req.getTitle().trim());
    }
    if (req.getDescription() != null) {
      task.setDescription(req.getDescription());
    }
    if (req.getStatus() != null) {
      task.setStatus(req.getStatus());
    }
    if (req.getPriority() != null) {
      task.setPriority(req.getPriority());
    }
    if (req.getAssigneeId() != null) {
      task.setAssignee(resolveAssignee(orgId, req.getAssigneeId()));
    }
    if (req.getBookId() != null) {
      task.setBook(resolveBook(orgId, req.getBookId()));
    }
    if (req.getManuscriptId() != null) {
      task.setManuscript(resolveManuscript(orgId, req.getManuscriptId()));
    }
    if (req.getDueDate() != null) {
      task.setDueDate(req.getDueDate());
    }
    return tasks.save(task);
  }

  @Transactional
  @Audited(action = "task.delete", entityType = "Task")
  public void delete(UUID id) {
    tasks.delete(get(id));
  }

  private AppUser resolveAssignee(UUID orgId, UUID assigneeId) {
    if (assigneeId == null) {
      return null;
    }
    return users.findByIdAndOrganizationId(assigneeId, orgId)
        .orElseThrow(() -> ApiException.notFound("User", assigneeId));
  }

  private Book resolveBook(UUID orgId, UUID bookId) {
    if (bookId == null) {
      return null;
    }
    return books.findByIdAndOrganizationId(bookId, orgId)
        .orElseThrow(() -> ApiException.notFound("Book", bookId));
  }

  private Manuscript resolveManuscript(UUID orgId, UUID manuscriptId) {
    if (manuscriptId == null) {
      return null;
    }
    return manuscripts.findByIdAndOrganizationId(manuscriptId, orgId)
        .orElseThrow(() -> ApiException.notFound("Manuscript", manuscriptId));
  }
}
