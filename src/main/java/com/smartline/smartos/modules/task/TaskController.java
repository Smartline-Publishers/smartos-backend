package com.smartline.smartos.modules.task;

import com.smartline.smartos.common.ApiResponse;
import com.smartline.smartos.common.PageResponse;
import com.smartline.smartos.modules.task.Task.TaskStatus;
import com.smartline.smartos.modules.task.TaskDtos.CreateTaskRequest;
import com.smartline.smartos.modules.task.TaskDtos.TaskMapper;
import com.smartline.smartos.modules.task.TaskDtos.TaskResponse;
import com.smartline.smartos.modules.task.TaskDtos.UpdateTaskRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

  private final TaskService service;

  public TaskController(TaskService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public ApiResponse<PageResponse<TaskResponse>> list(
      @RequestParam(required = false) TaskStatus status,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) UUID assigneeId,
      Pageable pageable) {
    return ApiResponse.ok(PageResponse.from(
        service.list(status, search, assigneeId, pageable).map(TaskMapper::toResponse)));
  }

  @GetMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  public ApiResponse<TaskResponse> get(@PathVariable UUID id) {
    return ApiResponse.ok(TaskMapper.toResponse(service.get(id)));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('ORG_ADMIN','EDITOR')")
  public ApiResponse<TaskResponse> create(@Valid @RequestBody CreateTaskRequest req) {
    return ApiResponse.ok(TaskMapper.toResponse(service.create(req)));
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN','EDITOR')")
  public ApiResponse<TaskResponse> update(@PathVariable UUID id,
      @RequestBody UpdateTaskRequest req) {
    return ApiResponse.ok(TaskMapper.toResponse(service.update(id, req)));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAnyRole('ORG_ADMIN','EDITOR')")
  public void delete(@PathVariable UUID id) {
    service.delete(id);
  }
}
