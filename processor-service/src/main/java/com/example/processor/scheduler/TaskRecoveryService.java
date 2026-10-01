package com.example.processor.processing;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskRecoveryService {

    private final ProcessingTaskRegistry registry;

    @Value("${processor.heartbeat.timeout-minutes:10}")
    private int heartbeatTimeoutMinutes;

    @PostConstruct
    public void recover() {
        log.info("🔄 Starting task recovery...");

        LocalDateTime threshold = LocalDateTime.now()
                .minusMinutes(heartbeatTimeoutMinutes);

        int recovered = registry.recoverStale(threshold);

        if (recovered > 0) {
            log.warn("♻️ Recovered {} stale IN_PROGRESS tasks (heartbeat older than {} min)",
                    recovered, heartbeatTimeoutMinutes);
        } else {
            log.info("🔄 No stale tasks to recover");
        }
    }
}