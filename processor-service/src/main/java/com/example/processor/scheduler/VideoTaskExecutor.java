package com.example.processor.scheduler;

import com.example.processor.processing.VideoProcessingService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
@Slf4j
public class VideoTaskExecutor {

    private final VideoTaskScheduler scheduler;
    private final VideoProcessingService processingService;

    @Value("${processor.threads.fast:2}")
    private int fastThreads;

    @Value("${processor.threads.slow:4}")
    private int slowThreads;

    @Value("${processor.threads.free:2}")
    private int freeThreads;

    private ExecutorService fastPool;
    private ExecutorService slowPool;
    private ExecutorService freePool;

    @PostConstruct
    public void start() {
        fastPool = Executors.newFixedThreadPool(fastThreads, named("fast"));
        slowPool = Executors.newFixedThreadPool(slowThreads, named("slow"));
        freePool = Executors.newFixedThreadPool(freeThreads, named("free"));

        for (int i = 0; i < fastThreads; i++) fastPool.submit(this::fastLoop);
        for (int i = 0; i < slowThreads; i++) slowPool.submit(this::slowLoop);
        for (int i = 0; i < freeThreads; i++) freePool.submit(this::freeLoop);

        log.info("🚀 Executor started: fast={}, slow={}, free={}",
                fastThreads, slowThreads, freeThreads);
    }

    private void fastLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                VideoTask task = scheduler.getFastQueue().take();
                process(task);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void slowLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                VideoTask task = scheduler.getSlowQueue().take();
                process(task);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /**
     * Свободные потоки: сначала fast, потом slow.
     * Ждём недолго в slow, чтобы не крутиться в busy-loop.
     */
    private void freeLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                VideoTask task = scheduler.getFastQueue().poll();
                if (task == null) {
                    task = scheduler.getSlowQueue().poll(1, TimeUnit.SECONDS);
                }
                if (task != null) {
                    process(task);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void process(VideoTask task) {
        long start = System.currentTimeMillis();
        try {
            log.info("▶️ [{}] videoId={} qualities={}",
                    task.priority(), task.event().getVideoId(),
                    task.qualities().stream().map(q -> q.name()).toList());
            processingService.process(task);
            log.info("✅ [{}] videoId={} done in {}s",
                    task.priority(), task.event().getVideoId(),
                    (System.currentTimeMillis() - start) / 1000);
        } catch (Exception e) {
            log.error("❌ [{}] videoId={} failed: {}",
                    task.priority(), task.event().getVideoId(), e.getMessage(), e);
        }
    }

    @PreDestroy
    public void stop() {
        fastPool.shutdownNow();
        slowPool.shutdownNow();
        freePool.shutdownNow();
    }

    private ThreadFactory named(String prefix) {
        AtomicInteger n = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, prefix + "-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }
}