package com.example.processor.metrics;

import com.example.processor.scheduler.TaskQueue;
import com.example.processor.scheduler.VideoTaskScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@EnableScheduling
public class QueueMetricsReporter {

    private final ProcessingMetrics metrics;
    private final TaskQueue queue;

    @Scheduled(fixedRate = 5000)
    public void report() {
        metrics.setQueueSize(ProcessingMetrics.FAST, queue.fastSize());
        metrics.setQueueSize(ProcessingMetrics.SLOW, queue.slowSize());
    }
}