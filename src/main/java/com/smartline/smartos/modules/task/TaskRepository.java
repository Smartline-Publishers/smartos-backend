package com.smartline.smartos.modules.task;

import com.smartline.smartos.modules.task.Task.TaskStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, UUID> {
  Optional<Task> findByIdAndOrganizationId(UUID id, UUID organizationId);
  Page<Task> findByOrganizationId(UUID organizationId, Pageable pageable);
  Page<Task> findByOrganizationIdAndStatus(UUID organizationId, TaskStatus status,
      Pageable pageable);
  Page<Task> findByOrganizationIdAndTitleContainingIgnoreCase(UUID organizationId, String title,
      Pageable pageable);
  Page<Task> findByOrganizationIdAndAssigneeId(UUID organizationId, UUID assigneeId,
      Pageable pageable);
}
