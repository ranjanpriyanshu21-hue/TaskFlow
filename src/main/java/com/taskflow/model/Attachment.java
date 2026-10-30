package com.taskflow.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter @Setter @NoArgsConstructor
public class Attachment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User uploadedBy;

    @Column(nullable = false)
    private String fileName;

    /** random name on disk, so uploads can never overwrite each other or escape the upload dir */
    @Column(nullable = false)
    private String storedName;

    private String contentType;
    private LocalDateTime uploadedAt = LocalDateTime.now();
}
