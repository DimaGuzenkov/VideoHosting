package com.example.processor.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessingMetrics {
    public static final String FAST = "fast";
    public static final String SLOW = "slow";


    private final MeterRegistry registry;

    private final Map<String, Long> receivedAt = new ConcurrentHashMap<>();

    private String key(Long videoId, String queueType) {
        return videoId + ":" + queueType;
    }

    @PostConstruct
    public void init() {
        log.info("ProcessingMetrics BEAN CREATED");
        registry.counter("processor.test.counter").increment();
    }

    // ===== 1. Задача пришла из Kafka ИЛИ перешла из одной очереди в другую =====
    public void onTaskReceived(Long videoId, String queueType) {
        receivedAt.put(key(videoId, queueType), System.currentTimeMillis());
        registry.counter("processor.tasks.received", "queue", queueType).increment();
        log.info("TASK_RECEIVED videoId={} queue={}", videoId, queueType);
    }

    // ===== 2. Поток взял задачу в работу =====
    public void onProcessingStarted(Long videoId, String queueType) {
        Long received = receivedAt.get(key(videoId, queueType));
        if (received == null) {
            log.warn("PROC_STARTED without received: videoId={} queue={}", videoId, queueType);
            return;
        }

        long waitMs = System.currentTimeMillis() - received;
        registry.timer("processor.queue.wait_seconds", "queue", queueType)
                .record(Duration.ofMillis(waitMs));
        registry.counter("processor.tasks.started", "queue", queueType).increment();

        log.info("PROC_STARTED videoId={} queue={} waitMs={}", videoId, queueType, waitMs);
    }

    // ===== 3a. 360p готов (fast queue) =====
    public void on360pReady(Long videoId) {
        Long received = receivedAt.remove(key(videoId, "fast"));
        if (received == null) return;

        long totalMs = System.currentTimeMillis() - received;
        registry.timer("processor.time_to_ready_seconds")
                .record(Duration.ofMillis(totalMs));
        registry.counter("processor.tasks.360p_completed").increment();

        log.debug("360P_READY videoId={} totalMs={}", videoId, totalMs);
    }

    // ===== 3b. Все качества готовы (slow queue) =====
    public void onFullReady(Long videoId) {
        Long received = receivedAt.remove(key(videoId, "slow"));
        if (received == null) return;

        long totalMs = System.currentTimeMillis() - received;
        registry.timer("processor.time_to_full_seconds")
                .record(Duration.ofMillis(totalMs));
        registry.counter("processor.tasks.full_completed").increment();

        log.debug("FULL_READY videoId={} totalMs={}", videoId, totalMs);
    }

    // ===== 4. Ошибка =====
    public void onTaskFailed(Long videoId, String queueType, String reason) {
        receivedAt.remove(key(videoId, queueType));
        registry.counter("processor.tasks.failed",
                "queue", queueType, "reason", reason).increment();
        log.debug("TASK_FAILED videoId={} queue={} reason={}", videoId, queueType, reason);
    }

    // ===== 5. Периодический размер очереди =====
    public void setQueueSize(String queueType, long size) {
        registry.gauge("processor.queue.size",
                Tags.of("queue", queueType),
                size);
    }
}