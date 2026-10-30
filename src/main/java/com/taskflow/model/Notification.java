package com.taskflow.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter @Setter @NoArgsConstructor
public class Notification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User recipient;

    @Column(nullable = false, length = 500)
    private String message;

    private Long projectId;
    private boolean seen;
    private LocalDateTime createdAt = LocalDateTime.now();
}
