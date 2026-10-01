package com.example.processor.scheduler;

import com.example.avro.VideoUploadedEvent;
import com.example.processor.ffmpeg.Quality;
import com.example.processor.metrics.ProcessingMetrics;
import com.example.processor.processing.ProcessingTaskRegistry;
import com.example.processor.scheduler.data.VideoTask;
import com.example.processor.scheduler.data.WorkerCapacityPlanner;
import com.example.processor.scheduler.data.WorkerRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Slf4j
public class VideoTaskScheduler {

    private final TaskQueue queues;
    private final WorkerCapacityPlanner planner;
    private final ProcessingTaskRegistry registry;
    private final ProcessingMetrics metrics;

    private final AtomicInteger freeOnSlow = new AtomicInteger(0);

    // === Submit ===

    public void submit(VideoUploadedEvent event) {
        registry.ensureTasksExist(event);
        enqueue(event,
                registry.filterPendingFast(event.getVideoId()),
                registry.filterPendingSlow(event.getVideoId()));
    }

    /** Пакетная постановка в очередь — используется и при submit, и при recovery. */
    public void enqueue(VideoUploadedEvent event, List<Quality> fast, List<Quality> slow) {
        queues.lock();
        try {
            Long videoId = event.getVideoId();
            if (!fast.isEmpty()) {
                queues.addFast(new VideoTask(event, fast, VideoTask.Priority.FAST));
                metrics.onTaskReceived(videoId, ProcessingMetrics.FAST);
            }
            if (!slow.isEmpty()) {
                queues.addSlow(new VideoTask(event, slow, VideoTask.Priority.SLOW));
                metrics.onTaskReceived(videoId, ProcessingMetrics.SLOW);
            }
            log.info("📥 Queued video {}: fast={}, slow={}, fastQ={}, slowQ={}",
                    videoId, fast.size(), slow.size(),
                    queues.fastSize(), queues.slowSize());
            queues.signalAll();
        } finally {
            queues.unlock();
        }
    }

    // === Acquire ===

    public VideoTask acquireTask(WorkerRole role) throws InterruptedException {
        queues.lock();
        try {
            while (true) {
                VideoTask task = pickTask(role);
                if (task != null) return task;
                queues.await();
            }
        } finally {
            queues.unlock();
        }
    }

    private VideoTask pickTask(WorkerRole role) {
        return switch (role) {
            case FAST -> queues.pollFast();
            case SLOW -> queues.pollSlow();
            case FREE -> pickForFree();
        };
    }

    private VideoTask pickForFree() {
        // 1. Fast — всегда приоритет
        VideoTask fast = queues.pollFast();
        if (fast != null) return fast;

        // 2. Slow — только если planner разрешает
        int target = planner.targetFreeOnSlow(queues.slowSize());
        if (freeOnSlow.get() < target) {
            VideoTask slow = queues.pollSlow();
            if (slow != null) {
                freeOnSlow.incrementAndGet();
                log.info("⚡ Free → slow (target={}, now={})", target, freeOnSlow.get());
            }
            return slow;
        }

        return null;
    }

    // === Completion ===

    public void onTaskCompleted(WorkerRole role, VideoTask task) {
        if (role == WorkerRole.FREE && task.priority() == VideoTask.Priority.SLOW) {
            freeOnSlow.decrementAndGet();
        }
        queues.lock();
        try {
            queues.signalAll();
        } finally {
            queues.unlock();
        }
    }

    public void removeFromQueues(Long videoId) {
        queues.lock();
        try {
            queues.removeByVideoId(videoId);
            log.info("🗑️ Removed videoId={} from queues", videoId);
        } finally {
            queues.unlock();
        }
    }
}