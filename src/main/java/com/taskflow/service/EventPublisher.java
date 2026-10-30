package com.taskflow.service;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;

/** Pushes WebSocket messages, but only after the surrounding DB transaction has committed. */
@Component
@RequiredArgsConstructor
public class EventPublisher {
    private final SimpMessagingTemplate template;

    public void projectChanged(Long projectId, String type, Long taskId) {
    Object payload = Map.of("type", type, "taskId", taskId == null ? 0L : taskId);
    String destination = "/topic/projects/" + projectId;
    afterCommit(() -> template.convertAndSend(destination, payload));
    }

    public void toUser(String username, Object payload) {
        afterCommit(() -> template.convertAndSendToUser(username, "/queue/notifications", payload));
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
