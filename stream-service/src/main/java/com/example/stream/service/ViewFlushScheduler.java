package com.example.stream.service;

import com.example.stream.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ViewFlushScheduler {

    private final ViewCounterService viewCounterService;
    private final VideoRepository videoRepository;

    /**
     * Раз в 30 секунд сбрасываем накопленные просмотры в Postgres.
     * fixedDelay — следующий запуск через 30 сек после завершения предыдущего.
     */
    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    @Transactional
    public void flush() {
        Map<Long, Long> deltas = viewCounterService.drainAll();
        if (deltas.isEmpty()) {
            return;
        }

//        log.info("💾 Flushing views for {} videos", deltas.size());
        for (Map.Entry<Long, Long> e : deltas.entrySet()) {
            try {
                videoRepository.addViews(e.getKey(), e.getValue());
            } catch (Exception ex) {
                log.error("Failed to flush views for videoId={}: {}", e.getKey(), ex.getMessage());
            }
        }
    }
}
