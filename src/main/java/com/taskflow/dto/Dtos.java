package com.taskflow.dto;

import com.taskflow.model.Priority;
import com.taskflow.model.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** Request/response shapes for the REST API. */
public final class Dtos {
    private Dtos() {}

    public record TaskRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 2000) String description,
            Priority priority,
            LocalDateTime dueDate,
            Long assigneeId) {}

    public record StatusRequest(TaskStatus status) {}

    public record CommentRequest(@NotBlank @Size(max = 2000) String text) {}

    public record TaskDto(
            Long id, Long projectId, String title, String description,
            TaskStatus status, Priority priority, LocalDateTime dueDate,
            Long assigneeId, String assigneeName, boolean overdue,
            long commentCount, long attachmentCount) {}

    public record CommentDto(Long id, String author, String text, LocalDateTime createdAt) {}

    public record AttachmentDto(Long id, String fileName, String uploadedBy, LocalDateTime uploadedAt) {}

    public record TaskDetailDto(TaskDto task, List<CommentDto> comments, List<AttachmentDto> attachments) {}

    public record MemberDto(Long id, String username, String fullName) {}

    public record NotificationDto(Long id, String message, Long projectId, boolean seen, LocalDateTime createdAt) {}

    public record ProjectStats(long total, long todo, long inProgress, long review, long done) {}
}
