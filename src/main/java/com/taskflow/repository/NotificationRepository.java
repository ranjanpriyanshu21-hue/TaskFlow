package com.taskflow.repository;

import com.taskflow.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findTop30ByRecipientIdOrderByCreatedAtDesc(Long userId);

    @Modifying
    @Query("update Notification n set n.seen = true where n.recipient.id = :userId and n.seen = false")
    void markAllSeen(@Param("userId") Long userId);
}
