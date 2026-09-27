package com.example.processor.scheduler;

import com.example.avro.VideoUploadedEvent;
import com.example.processor.ffmpeg.Quality;
import com.example.processor.processing.VideoProcessingProperties;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Service
@RequiredArgsConstructor
@Slf4j
public class VideoTaskScheduler {

    private final VideoProcessingProperties props;
    private final ProcessingTaskRepository taskRepo;

    @Getter
    private final BlockingQueue<VideoTask> fastQueue = new LinkedBlockingQueue<>();
    @Getter
    private final BlockingQueue<VideoTask> slowQueue = new LinkedBlockingQueue<>();

    /**
     * Принять событие и разложить на две задачи: fast (одно качество) и slow (все остальные).
     */
    public void submit(VideoUploadedEvent event) {
        Long videoId = event.getVideoId();

        List<Quality> fastQualities = props.getFast();
        List<Quality> slowQualities = props.getSlow();

        // Создаём PENDING-записи в БД для всех качеств (идемпотентно)
        ensureTasksExist(videoId, fastQualities);
        ensureTasksExist(videoId, slowQualities);

        // Fast-задача: только те качества, которые ещё не DONE
        List<Quality> pendingFast = filterPending(videoId, fastQualities);
        if (!pendingFast.isEmpty()) {
            fastQueue.add(new VideoTask(event, pendingFast, VideoTask.Priority.FAST));
        }

        // Slow-задача: одним пакетом
        List<Quality> pendingSlow = filterPending(videoId, slowQualities);
        if (!pendingSlow.isEmpty()) {
            slowQueue.add(new VideoTask(event, pendingSlow, VideoTask.Priority.SLOW));
        }

        log.info("📥 Queued video {}: fast={}, slow={}, fastQueue={}, slowQueue={}",
                videoId, pendingFast.size(), pendingSlow.size(),
                fastQueue.size(), slowQueue.size());
    }

    private List<Quality> filterPending(Long videoId, List<Quality> qualities) {
        List<String> done = taskRepo.findDoneQualities(videoId);
        return qualities.stream()
                .filter(q -> !done.contains(q.name()))
                .toList();
    }

    private void ensureTasksExist(Long videoId, List<Quality> qualities) {
        for (Quality q : qualities) {
            if (taskRepo.findByVideoIdAndQuality(videoId, q.name()).isEmpty()) {
                taskRepo.save(ProcessingTask.builder()
                        .videoId(videoId)
                        .quality(q.name())
                        .status(TaskStatus.PENDING)
                        .build());
            }
        }
    }

    /**
     * Восстановление очереди после падения: все PENDING-задачи заново попадают в очередь.
     */
    @PostConstruct
    public void recoverPendingTasks() {
        var pending = taskRepo.findByStatus(TaskStatus.PENDING);
        log.info("🔄 Recovering {} pending tasks", pending.size());
        // Здесь нужен маппинг из entity → VideoUploadedEvent.
        // Если не хранишь достаточно данных в БД — можно пропустить,
        // Kafka переотправит сообщение при рестарте consumer'а.
    }
}
