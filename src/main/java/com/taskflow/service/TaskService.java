package com.taskflow.service;

import com.taskflow.dto.Dtos.*;
import com.taskflow.model.*;
import com.taskflow.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TaskService {
    private final TaskRepository tasks;
    private final CommentRepository comments;
    private final AttachmentRepository attachments;
    private final UserRepository users;
    private final ProjectService projectService;
    private final NotificationService notifications;
    private final FileStorageService files;
    private final EventPublisher events;

    public record Download(String fileName, String contentType, Resource resource) {}

    // ---------------------------------------------------------------- board

    @Transactional(readOnly = true)
    public List<TaskDto> board(Long projectId, String username) {
        projectService.requireMember(projectId, username);
        return tasks.findByProjectIdOrderByCreatedAtAsc(projectId).stream().map(this::toDto).toList();
    }

    @Transactional
    public TaskDto create(Long projectId, TaskRequest req, String username) {
        Project project = projectService.requireMember(projectId, username);
        User me = user(username);

        Task t = new Task();
        t.setProject(project);
        t.setCreatedBy(me);
        apply(t, req);
        applyAssignee(t, req.assigneeId(), project, me);
        tasks.save(t);

        events.projectChanged(projectId, "created", t.getId());
        return toDto(t);
    }

    @Transactional
    public TaskDto update(Long taskId, TaskRequest req, String username) {
        Task t = loadForMember(taskId, username);
        User me = user(username);

        LocalDateTime oldDue = t.getDueDate();
        apply(t, req);
        if (!Objects.equals(oldDue, t.getDueDate())) {
            t.setReminderSent(false); // new deadline, so remind again
        }
        applyAssignee(t, req.assigneeId(), t.getProject(), me);

        events.projectChanged(t.getProject().getId(), "updated", t.getId());
        return toDto(t);
    }

    @Transactional
    public TaskDto move(Long taskId, TaskStatus status, String username) {
        if (status == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing status.");
        }
        Task t = loadForMember(taskId, username);
        User me = user(username);
        if (t.getStatus() != status) {
            t.setStatus(status);
            t.setCompletedAt(status == TaskStatus.DONE ? LocalDateTime.now() : null);
            if (t.getAssignee() != null && !t.getAssignee().getId().equals(me.getId())) {
                notifications.send(t.getAssignee(),
                        me.getFullName() + " moved \"" + t.getTitle() + "\" to " + label(status),
                        t.getProject().getId());
            }
            events.projectChanged(t.getProject().getId(), "moved", t.getId());
        }
        return toDto(t);
    }

    @Transactional
    public void delete(Long taskId, String username) {
        Task t = loadForMember(taskId, username);
        Long projectId = t.getProject().getId();
        attachments.findByTaskIdOrderByUploadedAtAsc(taskId).forEach(a -> files.delete(a.getStoredName()));
        attachments.deleteByTaskId(taskId);
        comments.deleteByTaskId(taskId);
        tasks.delete(t);
        events.projectChanged(projectId, "deleted", taskId);
    }

    // --------------------------------------------------------------- detail

    @Transactional(readOnly = true)
    public TaskDetailDto detail(Long taskId, String username) {
        Task t = loadForMember(taskId, username);
        List<CommentDto> cs = comments.findByTaskIdOrderByCreatedAtAsc(taskId).stream()
                .map(c -> new CommentDto(c.getId(), c.getAuthor().getFullName(), c.getText(), c.getCreatedAt()))
                .toList();
        List<AttachmentDto> as = attachments.findByTaskIdOrderByUploadedAtAsc(taskId).stream()
                .map(this::toDto).toList();
        return new TaskDetailDto(toDto(t), cs, as);
    }

    // ------------------------------------------------------------- comments

    @Transactional
    public CommentDto addComment(Long taskId, String text, String username) {
        Task t = loadForMember(taskId, username);
        User me = user(username);

        Comment c = new Comment();
        c.setTask(t);
        c.setAuthor(me);
        c.setText(text.trim());
        comments.save(c);

        String msg = me.getFullName() + " commented on \"" + t.getTitle() + "\"";
        notifyIfOther(t.getAssignee(), me, msg, t);
        if (t.getCreatedBy() != null && (t.getAssignee() == null
                || !t.getAssignee().getId().equals(t.getCreatedBy().getId()))) {
            notifyIfOther(t.getCreatedBy(), me, msg, t);
        }
        events.projectChanged(t.getProject().getId(), "commented", t.getId());
        return new CommentDto(c.getId(), me.getFullName(), c.getText(), c.getCreatedAt());
    }

    // ---------------------------------------------------------- attachments

    @Transactional
    public AttachmentDto addAttachment(Long taskId, MultipartFile file, String username) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a file to upload.");
        }
        Task t = loadForMember(taskId, username);
        User me = user(username);

        Attachment a = new Attachment();
        a.setTask(t);
        a.setUploadedBy(me);
        a.setFileName(safeName(file.getOriginalFilename()));
        a.setContentType(file.getContentType());
        a.setStoredName(files.store(file));
        attachments.save(a);

        notifyIfOther(t.getAssignee(), me,
                me.getFullName() + " attached " + a.getFileName() + " to \"" + t.getTitle() + "\"", t);
        events.projectChanged(t.getProject().getId(), "attached", t.getId());
        return toDto(a);
    }

    @Transactional(readOnly = true)
    public Download download(Long attachmentId, String username) {
        Attachment a = attachments.findById(attachmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found."));
        projectService.requireMember(a.getTask().getProject().getId(), username);
        return new Download(a.getFileName(), a.getContentType(), files.load(a.getStoredName()));
    }

    // -------------------------------------------------------------- helpers

    private void apply(Task t, TaskRequest req) {
        t.setTitle(req.title().trim());
        t.setDescription(req.description() == null || req.description().isBlank() ? null : req.description().trim());
        t.setPriority(req.priority() == null ? Priority.MEDIUM : req.priority());
        t.setDueDate(req.dueDate());
    }

    private void applyAssignee(Task t, Long assigneeId, Project project, User actor) {
        User newAssignee = null;
        if (assigneeId != null) {
            newAssignee = project.getMembers().stream()
                    .filter(m -> m.getId().equals(assigneeId)).findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "You can only assign tasks to people on this project."));
        }
        Long before = t.getAssignee() == null ? null : t.getAssignee().getId();
        Long after = newAssignee == null ? null : newAssignee.getId();
        t.setAssignee(newAssignee);
        if (after != null && !after.equals(before)) {
            t.setReminderSent(false);
            notifyIfOther(newAssignee, actor,
                    actor.getFullName() + " assigned you \"" + t.getTitle() + "\"", t);
        }
    }

    private void notifyIfOther(User recipient, User actor, String message, Task t) {
        if (recipient != null && !recipient.getId().equals(actor.getId())) {
            notifications.send(recipient, message, t.getProject().getId());
        }
    }

    private Task loadForMember(Long taskId, String username) {
        Task t = tasks.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found."));
        projectService.requireMember(t.getProject().getId(), username);
        return t;
    }

    private User user(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in again."));
    }

    private TaskDto toDto(Task t) {
        User a = t.getAssignee();
        boolean overdue = t.getDueDate() != null && t.getStatus() != TaskStatus.DONE
                && t.getDueDate().isBefore(LocalDateTime.now());
        return new TaskDto(t.getId(), t.getProject().getId(), t.getTitle(), t.getDescription(),
                t.getStatus(), t.getPriority(), t.getDueDate(),
                a == null ? null : a.getId(), a == null ? null : a.getFullName(), overdue,
                comments.countByTaskId(t.getId()), attachments.countByTaskId(t.getId()));
    }

    private AttachmentDto toDto(Attachment a) {
        return new AttachmentDto(a.getId(), a.getFileName(), a.getUploadedBy().getFullName(), a.getUploadedAt());
    }

    /** Strips any directory part a client may send and limits the length. */
    private static String safeName(String original) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).trim();
        if (name.isEmpty()) name = "file";
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
    }

    private static String label(TaskStatus s) {
        return switch (s) {
            case TODO -> "To do";
            case IN_PROGRESS -> "In progress";
            case REVIEW -> "In review";
            case DONE -> "Done";
        };
    }
}
