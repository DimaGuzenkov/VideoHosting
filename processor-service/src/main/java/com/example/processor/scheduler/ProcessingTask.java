package com.example.processor.scheduler;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "video_processing_tasks",
        uniqueConstraints = @UniqueConstraint(columnNames = {"videoId", "quality"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessingTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long videoId;

    @Column(nullable = false, length = 16)
    private String quality;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TaskStatus status;

    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
