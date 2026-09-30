package com.example.processor.scheduler;

import com.example.processor.metrics.ProcessingMetrics;
import com.example.processor.processing.VideoProcessingService;
import com.example.processor.scheduler.data.VideoTask;
import com.example.processor.scheduler.data.WorkerRole;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
@RequiredArgsConstructor
@Slf4j
public class VideoTaskExecutor {

    private final VideoTaskScheduler scheduler;
    private final VideoProcessingService processingService;
    private final ProcessingMetrics metrics;

    @Value("${processor.threads.fast:2}") private int fastThreads;
    @Value("${processor.threads.slow:4}") private int slowThreads;
    @Value("${processor.threads.free:2}") private int freeThreads;

    private final List<ExecutorService> pools = new ArrayList<>();

    @PostConstruct
    public void start() {
        startPool("fast", fastThreads, WorkerRole.FAST);
        startPool("slow", slowThreads, WorkerRole.SLOW);
        startPool("free", freeThreads, WorkerRole.FREE);
        log.info("🚀 Executor started: fast={}, slow={}, free={}",
                fastThreads, slowThreads, freeThreads);
    }

    private void startPool(String name, int size, WorkerRole role) {
        ExecutorService pool = Executors.newFixedThreadPool(size, r -> {
            Thread t = new Thread(r, name + "-" + UUID.randomUUID().toString().substring(0, 4));
            t.setDaemon(true);
            return t;
        });
        for (int i = 0; i < size; i++) {
            pool.submit(() -> workerLoop(role));
        }
        pools.add(pool);
    }

    private void workerLoop(WorkerRole role) {
        while (!Thread.currentThread().isInterrupted()) {
            VideoTask task = null;
            try {
                task = scheduler.acquireTask(role);
                long start = System.currentTimeMillis();
                Long videoId = task.event().getVideoId();

                if (task.priority() == VideoTask.Priority.FAST) {
                    metrics.onProcessingStarted(videoId, ProcessingMetrics.FAST);
                } else {
                    metrics.onProcessingStarted(videoId, ProcessingMetrics.SLOW);
                }

                processingService.process(task);

                if (task.priority() == VideoTask.Priority.FAST) {
                    metrics.on360pReady(videoId);
                } else {
                    metrics.onFullReady(videoId);
                }

                log.info("✅ [{}] videoId={} done in {}s",
                        role, videoId,
                        (System.currentTimeMillis() - start) / 1000);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.error("❌ [{}] task failed: {}", role, e.getMessage(), e);
            } finally {
                // ВАЖНО: уведомляем scheduler о завершении (для freeOnSlow--)
                if (task != null) {
                    scheduler.onTaskCompleted(role, task);
                }
            }
        }
    }

    @PreDestroy
    public void stop() {
        pools.forEach(ExecutorService::shutdownNow);
    }
}