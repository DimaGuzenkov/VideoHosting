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

    @Query("SELECT t.quality FROM ProcessingTask t " +
            "WHERE t.videoId = :videoId AND t.status = 'DONE'")
    List<String> findDoneQualities(@Param("videoId") Long videoId);

    /**
     * Атомарный claim одного качества: переводит PENDING → IN_PROGRESS.
     * Возвращает 1, если удалось (задача была PENDING), иначе 0.
     */
    @Modifying
    @Query("UPDATE ProcessingTask t SET t.status = 'IN_PROGRESS', t.startedAt = :now " +
            "WHERE t.videoId = :videoId AND t.quality = :quality AND t.status = 'PENDING'")
    int claim(@Param("videoId") Long videoId,
              @Param("quality") String quality,
              @Param("now") LocalDateTime now);

    /**
     * Восстановление зависших IN_PROGRESS (если процессор упал в середине).
     */
    @Modifying
    @Query("UPDATE ProcessingTask t SET t.status = 'PENDING', t.startedAt = null " +
            "WHERE t.status = 'IN_PROGRESS' AND t.startedAt < :threshold")
    int reclaimStale(@Param("threshold") LocalDateTime threshold);

    @Modifying
    @Query("UPDATE ProcessingTask t SET t.status = 'PENDING', t.startedAt = null " +
            "WHERE t.status = 'IN_PROGRESS'")
    int reclaimAllInProgress();

    Optional<ProcessingTask> findByVideoIdAndQuality(Long videoId, String quality);

    List<ProcessingTask> findByStatus(TaskStatus status);
}
