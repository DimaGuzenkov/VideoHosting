package com.example.processor.db;

import com.example.processor.scheduler.data.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ProcessingTaskRepository extends JpaRepository<ProcessingTask, Long> {

    Optional<ProcessingTask> findByVideoIdAndQuality(Long videoId, String quality);

    List<ProcessingTask> findByVideoId(Long videoId);

    List<ProcessingTask> findByStatus(TaskStatus status);

    @Query("SELECT t.quality FROM ProcessingTask t " +
            "WHERE t.videoId = :videoId AND t.status = 'DONE'")
    List<String> findDoneQualities(@Param("videoId") Long videoId);

    @Query("SELECT COUNT(t) FROM ProcessingTask t " +
            "WHERE t.videoId = :videoId AND t.status = 'CANCELLED'")
    long countCancelled(@Param("videoId") Long videoId);

    // === Insert (idempotent) ===

    @Modifying
    @Query(value = "INSERT INTO video_processing_tasks " +
            "(video_id, quality, status, user_id, file_path, created_at) " +
            "VALUES (:videoId, :quality, 'PENDING', :userId, :filePath, NOW()) " +
            "ON CONFLICT (video_id, quality) DO NOTHING",
            nativeQuery = true)
    void insertIfNotExists(@Param("videoId") Long videoId,
                           @Param("quality") String quality,
                           @Param("userId") Long userId,
                           @Param("filePath") String filePath);

    // === Claim (SKIP LOCKED, группировка по видео) ===

    @Query(value = """
        WITH next_video AS (
            SELECT video_id
            FROM video_processing_tasks
            WHERE status = 'PENDING'
              AND quality = ANY(:qualities)
            GROUP BY video_id
            ORDER BY MIN(created_at)
            LIMIT 1
        ),
        claimed AS (
            UPDATE video_processing_tasks
            SET status = 'IN_PROGRESS',
                worker_id = :workerId,
                started_at = NOW(),
                heartbeat_at = NOW()
            WHERE video_id IN (SELECT video_id FROM next_video)
              AND quality = ANY(:qualities)
              AND status = 'PENDING'
            RETURNING *
        )
        SELECT * FROM claimed
        """, nativeQuery = true)
    List<ProcessingTask> claimNextBatch(@Param("qualities") String[] qualities,
                                        @Param("workerId") String workerId);

    // === Heartbeat ===

    @Modifying
    @Query("UPDATE ProcessingTask t " +
            "SET t.heartbeatAt = :now " +
            "WHERE t.workerId = :workerId AND t.status = 'IN_PROGRESS'")
    int updateHeartbeat(@Param("workerId") String workerId,
                        @Param("now") LocalDateTime now);

    // === Mark done / failed / cancelled ===

    @Modifying
    @Query("UPDATE ProcessingTask t " +
            "SET t.status = 'DONE', t.finishedAt = :now " +
            "WHERE t.videoId = :videoId AND t.quality IN :qualities " +
            "  AND t.status != 'CANCELLED'")
    int markDone(@Param("videoId") Long videoId,
                 @Param("qualities") List<String> qualities,
                 @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE ProcessingTask t " +
            "SET t.status = 'FAILED', t.finishedAt = :now " +
            "WHERE t.videoId = :videoId AND t.quality IN :qualities")
    int markFailed(@Param("videoId") Long videoId,
                   @Param("qualities") List<String> qualities,
                   @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE ProcessingTask t " +
            "SET t.status = 'CANCELLED', t.finishedAt = :now " +
            "WHERE t.videoId = :videoId AND t.status = 'IN_PROGRESS'")
    int markCancelledByVideoId(@Param("videoId") Long videoId,
                               @Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM ProcessingTask t " +
            "WHERE t.videoId = :videoId AND t.status = 'PENDING'")
    int deletePendingByVideoId(@Param("videoId") Long videoId);

    // === Recovery ===

    @Modifying
    @Query("UPDATE ProcessingTask t " +
            "SET t.status = 'PENDING', t.workerId = NULL, " +
            "    t.startedAt = NULL, t.heartbeatAt = NULL " +
            "WHERE t.status = 'IN_PROGRESS' AND t.heartbeatAt < :threshold")
    int recoverStale(@Param("threshold") LocalDateTime threshold);

    @Modifying
    @Query("UPDATE ProcessingTask t " +
            "SET t.status = 'PENDING', t.workerId = NULL, " +
            "    t.startedAt = NULL, t.heartbeatAt = NULL " +
            "WHERE t.status = 'IN_PROGRESS' AND t.workerId = :workerId")
    int recoverByWorkerId(@Param("workerId") String workerId);
}