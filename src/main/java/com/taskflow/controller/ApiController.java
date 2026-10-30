package com.taskflow.controller;

import com.taskflow.dto.Dtos.*;
import com.taskflow.service.NotificationService;
import com.taskflow.service.ProjectService;
import com.taskflow.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {
    private final TaskService tasks;
    private final ProjectService projects;
    private final NotificationService notifications;

    // ---- board ----
    @GetMapping("/projects/{projectId}/tasks")
    public List<TaskDto> board(@PathVariable Long projectId, Principal p) {
        return tasks.board(projectId, p.getName());
    }

    @GetMapping("/projects/{projectId}/members")
    public List<MemberDto> members(@PathVariable Long projectId, Principal p) {
        return projects.members(projectId, p.getName());
    }

    @PostMapping("/projects/{projectId}/tasks")
    public TaskDto create(@PathVariable Long projectId, @Valid @RequestBody TaskRequest req, Principal p) {
        return tasks.create(projectId, req, p.getName());
    }

    // ---- task ----
    @GetMapping("/tasks/{id}")
    public TaskDetailDto detail(@PathVariable Long id, Principal p) {
        return tasks.detail(id, p.getName());
    }

    @PutMapping("/tasks/{id}")
    public TaskDto update(@PathVariable Long id, @Valid @RequestBody TaskRequest req, Principal p) {
        return tasks.update(id, req, p.getName());
    }

    @PatchMapping("/tasks/{id}/status")
    public TaskDto move(@PathVariable Long id, @RequestBody StatusRequest req, Principal p) {
        return tasks.move(id, req.status(), p.getName());
    }

    @DeleteMapping("/tasks/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Principal p) {
        tasks.delete(id, p.getName());
        return ResponseEntity.noContent().build();
    }

    // ---- collaboration ----
    @PostMapping("/tasks/{id}/comments")
    public CommentDto comment(@PathVariable Long id, @Valid @RequestBody CommentRequest req, Principal p) {
        return tasks.addComment(id, req.text(), p.getName());
    }

    @PostMapping(value = "/tasks/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AttachmentDto attach(@PathVariable Long id, @RequestParam("file") MultipartFile file, Principal p) {
        return tasks.addAttachment(id, file, p.getName());
    }

    @GetMapping("/attachments/{id}/download")
    public ResponseEntity<org.springframework.core.io.Resource> download(@PathVariable Long id, Principal p) {
        TaskService.Download d = tasks.download(id, p.getName());
        // always "attachment" so an uploaded .html file can never run inside our origin
        ContentDisposition cd = ContentDisposition.attachment().filename(d.fileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(d.resource());
    }

    // ---- notifications ----
    @GetMapping("/notifications")
    public List<NotificationDto> notifications(Principal p) {
        return notifications.latest(p.getName());
    }

    @PostMapping("/notifications/read")
    public ResponseEntity<Void> markRead(Principal p) {
        notifications.markAllSeen(p.getName());
        return ResponseEntity.noContent().build();
    }
}
