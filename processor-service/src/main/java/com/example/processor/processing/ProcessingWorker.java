package com.example.processor.processing;

import com.example.processor.ProcessorMode;
import com.example.processor.db.ProcessingTask;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProcessingWorker {

    private final ProcessingTaskRegistry registry;
    private final VideoProcessingService processingService;

    @Value("${processor.mode}")
    private ProcessorMode mode;

    @Value("${processor.threads:2}")
    private int threads;

    @Value("${processor.worker-id:#{null}}")
    private String configuredWorkerId;

    private String workerId;
    private ExecutorService pool;

    @PostConstruct
    public void start() {
        this.workerId = configuredWorkerId != null
                ? configuredWorkerId
                : resolveHostname();

        this.pool = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "worker-" + UUID.randomUUID().toString().substring(0, 4));
            t.setDaemon(true);
            return t;
        });

        for (int i = 0; i < threads; i++) {
            pool.submit(this::loop);
        }

        log.info("🚀 [{}] Started {} workers, workerId={}",
                mode, threads, workerId);
    }

    private String resolveHostname() {
        String hostname = System.getenv("HOSTNAME");
        if (hostname == null || hostname.isBlank()) {
            hostname = "local-" + UUID.randomUUID().toString().substring(0, 8);
        }
        return hostname;
    }

    private void loop() {
        while (!Thread.currentThread().isInterrupted()) {
            List<ProcessingTask> batch = List.of();
            try {
                batch = registry.claimNextBatch(workerId, mode);

                if (batch.isEmpty()) {
                    Thread.sleep(500);
                    continue;
                }

                processingService.process(batch, workerId);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.error("❌ [{}] Worker loop error: {}", mode, e.getMessage(), e);
                if (!batch.isEmpty()) {
                    try {
                        List<String> qualities = batch.stream()
                                .map(ProcessingTask::getQuality)
                                .toList();
                        registry.markFailed(batch.get(0).getVideoId(), qualities);
                    } catch (Exception ex) {
                        log.error("Failed to mark tasks as FAILED", ex);
                    }
                }
            }
        }
    }

    @PreDestroy
    public void stop() {
        if (pool != null) {
            pool.shutdownNow();
        }
    }
}