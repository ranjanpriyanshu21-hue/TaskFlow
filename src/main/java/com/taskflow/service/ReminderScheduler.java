package com.taskflow.service;

import com.taskflow.model.Task;
import com.taskflow.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Checks every minute for tasks that are due soon or overdue and reminds the assignee once. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderScheduler {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("d MMM, HH:mm");

    private final TaskRepository tasks;
    private final NotificationService notifications;

    @Value("${app.reminder.lead-hours:24}")
    private long leadHours;

    @Scheduled(fixedDelay = 60_000, initialDelay = 15_000)
    @Transactional
    public void sendReminders() {
        LocalDateTime now = LocalDateTime.now();
        List<Task> due = tasks.findDueForReminder(now.plusHours(leadHours));
        for (Task t : due) {
            String message = t.getDueDate().isBefore(now)
                    ? "Overdue: \"" + t.getTitle() + "\" was due " + FMT.format(t.getDueDate())
                    : "Due soon: \"" + t.getTitle() + "\" is due " + FMT.format(t.getDueDate());
            notifications.send(t.getAssignee(), message, t.getProject().getId());
            t.setReminderSent(true);
        }
        if (!due.isEmpty()) {
            log.info("Sent {} deadline reminder(s)", due.size());
        }
    }
}
