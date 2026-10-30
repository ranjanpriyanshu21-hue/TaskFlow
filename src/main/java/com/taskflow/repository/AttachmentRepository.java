package com.taskflow.repository;

import com.taskflow.model.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
    List<Attachment> findByTaskIdOrderByUploadedAtAsc(Long taskId);
    long countByTaskId(Long taskId);
    void deleteByTaskId(Long taskId);
}
