package com.smartline.smartos.modules.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartline.smartos.common.exception.ApiException;
import com.smartline.smartos.modules.book.BookRepository;
import com.smartline.smartos.modules.manuscript.ManuscriptRepository;
import com.smartline.smartos.modules.organization.Organization;
import com.smartline.smartos.modules.organization.OrganizationRepository;
import com.smartline.smartos.modules.task.Task.TaskPriority;
import com.smartline.smartos.modules.task.Task.TaskStatus;
import com.smartline.smartos.modules.task.TaskDtos.CreateTaskRequest;
import com.smartline.smartos.modules.task.TaskDtos.UpdateTaskRequest;
import com.smartline.smartos.modules.user.AppUserRepository;
import com.smartline.smartos.tenant.TenantContext;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

  @Mock private TaskRepository tasks;
  @Mock private OrganizationRepository organizations;
  @Mock private AppUserRepository users;
  @Mock private BookRepository books;
  @Mock private ManuscriptRepository manuscripts;

  private TaskService service;
  private UUID orgId;
  private Organization org;

  @BeforeEach
  void setUp() {
    service = new TaskService(tasks, organizations, users, books, manuscripts);
    orgId = UUID.randomUUID();
    TenantContext.setCurrentOrg(orgId);
    org = new Organization();
    org.setId(orgId);
    org.setName("Acme");
    org.setSlug("acme");
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void createDefaultsToTodoMedium() {
    when(organizations.findById(orgId)).thenReturn(Optional.of(org));
    when(tasks.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

    CreateTaskRequest req = new CreateTaskRequest();
    req.setTitle("Review proof");
    Task saved = service.create(req);

    assertEquals(TaskStatus.TODO, saved.getStatus());
    assertEquals(TaskPriority.MEDIUM, saved.getPriority());
    assertEquals(orgId, saved.getOrganization().getId());
  }

  @Test
  void createWithForeignBookReturns404() {
    UUID foreignBook = UUID.randomUUID();
    when(organizations.findById(orgId)).thenReturn(Optional.of(org));
    when(books.findByIdAndOrganization_Id(eq(foreignBook), eq(orgId))).thenReturn(Optional.empty());

    CreateTaskRequest req = new CreateTaskRequest();
    req.setTitle("Linked task");
    req.setBookId(foreignBook);

    ApiException ex = assertThrows(ApiException.class, () -> service.create(req));

    assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
  }

  @Test
  void listIsScopedToTenant() {
    var pageable = PageRequest.of(0, 10);
    when(tasks.findByOrganization_Id(eq(orgId), eq(pageable))).thenReturn(Page.empty(pageable));

    service.list(null, null, null, pageable);

    verify(tasks).findByOrganization_Id(eq(orgId), eq(pageable));
  }

  @Test
  void updateStatus() {
    Task task = new Task();
    task.setId(UUID.randomUUID());
    task.setOrganization(org);
    task.setTitle("Old");
    task.setStatus(TaskStatus.TODO);
    when(tasks.findByIdAndOrganization_Id(eq(task.getId()), eq(orgId)))
        .thenReturn(Optional.of(task));
    when(tasks.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

    UpdateTaskRequest req = new UpdateTaskRequest();
    req.setStatus(TaskStatus.IN_PROGRESS);

    assertEquals(TaskStatus.IN_PROGRESS, service.update(task.getId(), req).getStatus());
  }

  @Test
  void getForeignTaskReturns404() {
    UUID other = UUID.randomUUID();
    when(tasks.findByIdAndOrganization_Id(eq(other), eq(orgId))).thenReturn(Optional.empty());

    ApiException ex = assertThrows(ApiException.class, () -> service.get(other));

    assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
  }
}
