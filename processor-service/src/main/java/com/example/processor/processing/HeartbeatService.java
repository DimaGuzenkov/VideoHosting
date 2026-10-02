package com.example.processor.processing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class HeartbeatService {

    private final ProcessingTaskRegistry registry;

    @Value("${processor.worker-id:#{null}}")
    private String configuredWorkerId;

    private String workerId;

    @jakarta.annotation.PostConstruct
    public void init() {
        this.workerId = configuredWorkerId != null
                ? configuredWorkerId
                : System.getenv("HOSTNAME");
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void beat() {
        try {
            int updated = registry.updateHeartbeatSafe(workerId);
            if (updated > 0) {
                log.debug("💓 Heartbeat updated for {} tasks (workerId={})",
                        updated, workerId);
            }
        } catch (Exception e) {
            log.warn("Heartbeat failed: {}", e.getMessage());
        }
    }
}