package com.taskflow.service;

import com.taskflow.dto.Dtos.NotificationDto;
import com.taskflow.model.Notification;
import com.taskflow.model.User;
import com.taskflow.repository.NotificationRepository;
import com.taskflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository repo;
    private final UserRepository users;
    private final EventPublisher events;

    /** Stores a notification and pushes it live to the recipient if they are online. */
    @Transactional
    public void send(User recipient, String message, Long projectId) {
        Notification n = new Notification();
        n.setRecipient(recipient);
        n.setMessage(message.length() > 500 ? message.substring(0, 500) : message);
        n.setProjectId(projectId);
        repo.save(n);
        events.toUser(recipient.getUsername(), toDto(n));
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> latest(String username) {
        User u = users.findByUsername(username).orElseThrow();
        return repo.findTop30ByRecipientIdOrderByCreatedAtDesc(u.getId()).stream().map(this::toDto).toList();
    }

    @Transactional
    public void markAllSeen(String username) {
        User u = users.findByUsername(username).orElseThrow();
        repo.markAllSeen(u.getId());
    }

    private NotificationDto toDto(Notification n) {
        return new NotificationDto(n.getId(), n.getMessage(), n.getProjectId(), n.isSeen(), n.getCreatedAt());
    }
}
