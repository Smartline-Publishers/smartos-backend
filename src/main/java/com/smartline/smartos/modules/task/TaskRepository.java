package com.smartline.smartos.modules.task;

import com.smartline.smartos.modules.task.Task.TaskStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, UUID> {
  Optional<Task> findByIdAndOrganization_Id(UUID id, UUID organizationId);
  Page<Task> findByOrganization_Id(UUID organizationId, Pageable pageable);
  Page<Task> findByOrganization_IdAndStatus(UUID organizationId, TaskStatus status,
      Pageable pageable);
  Page<Task> findByOrganization_IdAndTitleContainingIgnoreCase(UUID organizationId, String title,
      Pageable pageable);
  Page<Task> findByOrganization_IdAndAssigneeId(UUID organizationId, UUID assigneeId,
      Pageable pageable);
}
