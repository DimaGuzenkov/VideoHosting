package com.example.processor.kafka;

import com.example.avro.VideoDeletedEvent;
import com.example.processor.processing.ProcessingTaskRegistry;
import com.example.processor.scheduler.VideoTaskScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class VideoDeletedListener {

    private final ProcessingTaskRegistry registry;
    private final VideoTaskScheduler scheduler;

    @KafkaListener(topics = "video.deleted", groupId = "video-processor-deleted")
    public void onVideoDeleted(VideoDeletedEvent event) {
        Long videoId = event.getVideoId();
        log.info("🗑️ Received video.deleted: videoId={}", videoId);

        // 1. Удаляем PENDING-задачи (они ещё не в работе)
        int deleted = registry.deletePendingByVideoId(videoId);
        log.info("🗑️ Deleted {} PENDING tasks for videoId={}", deleted, videoId);

        // 2. Помечаем IN_PROGRESS как CANCELLED (воркеры доработают, но результат выбросят)
        int cancelled = registry.markCancelledByVideoId(videoId);
        log.info("🗑️ Marked {} IN_PROGRESS tasks as CANCELLED for videoId={}",
                cancelled, videoId);

        // 3. Убираем из in-memory очередей (если ещё не взяты воркерами)
        scheduler.removeFromQueues(videoId);
    }
}