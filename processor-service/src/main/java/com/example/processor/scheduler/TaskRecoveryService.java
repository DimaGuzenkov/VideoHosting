package com.example.processor.scheduler;

import com.example.avro.VideoUploadedEvent;
import com.example.processor.db.ProcessingTask;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskRecoveryService {

    private final ProcessingTaskRegistry registry;
    private final VideoTaskScheduler scheduler;

    @PostConstruct
    public void recover() {
        log.info("🔄 Starting task recovery...");

        int reclaimed = registry.reclaimStale(LocalDateTime.now().minusMinutes(30));
        if (reclaimed > 0) {
            log.warn("♻️ Reclaimed {} stale IN_PROGRESS tasks", reclaimed);
        }

        Map<Long, List<ProcessingTask>> byVideo = registry.findPendingGroupedByVideo();
        if (byVideo.isEmpty()) {
            log.info("🔄 Nothing to recover");
            return;
        }

        int recovered = 0;
        for (var entry : byVideo.entrySet()) {
            Long videoId = entry.getKey();
            List<ProcessingTask> tasks = entry.getValue();

            ProcessingTask first = tasks.get(0);
            VideoUploadedEvent event = VideoUploadedEvent.newBuilder()
                    .setVideoId(videoId)
                    .setUserId(first.getUserId())
                    .setFilePath(first.getFilePath())
                    .build();

            var split = registry.splitByPriority(tasks);
            scheduler.enqueue(event, split.fast(), split.slow());
            recovered++;
        }

        log.info("🔄 Recovered {} videos from DB", recovered);
    }
}