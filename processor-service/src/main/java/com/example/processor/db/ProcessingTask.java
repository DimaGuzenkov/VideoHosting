package com.example.processor.db;

import com.example.processor.scheduler.data.TaskStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "video_processing_tasks",
        uniqueConstraints = @UniqueConstraint(columnNames = {"video_id", "quality"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessingTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "video_id", nullable = false)
    private Long videoId;

    @Column(nullable = false, length = 16)
    private String quality;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TaskStatus status;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "file_path", length = 512)
    private String filePath;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;
}