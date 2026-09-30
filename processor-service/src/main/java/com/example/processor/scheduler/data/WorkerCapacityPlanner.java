package com.example.processor.scheduler.data;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WorkerCapacityPlanner {

    @Value("${processor.slow.tasks-per-worker:3}")
    private int slowTasksPerWorker;

    @Value("${processor.threads.slow:2}")
    private int slowWorkersCount;

    public int targetFreeOnSlow(int slowQueueSize) {
        int required = (int) Math.ceil((double) slowQueueSize / slowTasksPerWorker);
        return Math.max(0, required - slowWorkersCount);
    }
}