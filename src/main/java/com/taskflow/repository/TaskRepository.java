package com.taskflow.repository;

import com.taskflow.model.Task;
import com.taskflow.model.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByProjectIdOrderByCreatedAtAsc(Long projectId);

    long countByProjectId(Long projectId);

    long countByProjectIdAndStatus(Long projectId, TaskStatus status);

    /** Open, assigned tasks whose deadline falls before {@code limit} and that have not been reminded yet. */
    @Query("select t from Task t where t.dueDate is not null and t.dueDate <= :limit "
            + "and t.reminderSent = false and t.assignee is not null "
            + "and t.status <> com.taskflow.model.TaskStatus.DONE")
    List<Task> findDueForReminder(@Param("limit") LocalDateTime limit);
}
