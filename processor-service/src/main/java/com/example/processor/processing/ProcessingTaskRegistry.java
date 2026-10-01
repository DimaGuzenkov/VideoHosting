package com.example.processor.processing;

import com.example.avro.VideoUploadedEvent;
import com.example.processor.db.ProcessingTask;
import com.example.processor.db.ProcessingTaskRepository;
import com.example.processor.ffmpeg.Quality;
import com.example.processor.scheduler.data.TaskStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessingTaskRegistry {

    private final ProcessingTaskRepository taskRepo;
    private final VideoProcessingProperties props;


    @Transactional
    public void ensureTasksExist(VideoUploadedEvent event) {
        Long videoId = event.getVideoId();
        Long userId = event.getUserId();
        String filePath = event.getFilePath().toString();

        ensureForQualities(videoId, userId, filePath, props.getFast());
        ensureForQualities(videoId, userId, filePath, props.getSlow());
    }

    private void ensureForQualities(Long videoId, Long userId, String filePath, List<Quality> qualities) {
        for (Quality q : qualities) {
            var existing = taskRepo.findByVideoIdAndQuality(videoId, q.name());
            if (existing.isEmpty()) {
                taskRepo.save(ProcessingTask.builder()
                        .videoId(videoId)
                        .quality(q.name())
                        .status(TaskStatus.PENDING)
                        .userId(userId)
                        .filePath(filePath)
                        .build());
            } else {
                var task = existing.get();
                if (task.getUserId() == null) task.setUserId(userId);
                if (task.getFilePath() == null) task.setFilePath(filePath);
            }
        }
    }

    public List<Quality> filterPendingFast(Long videoId) {
        return filterPending(videoId, props.getFast());
    }

    public List<Quality> filterPendingSlow(Long videoId) {
        return filterPending(videoId, props.getSlow());
    }

    private List<Quality> filterPending(Long videoId, List<Quality> qualities) {
        List<String> done = taskRepo.findDoneQualities(videoId);
        return qualities.stream()
                .filter(q -> !done.contains(q.name()))
                .filter(q -> {
                    var t = taskRepo.findByVideoIdAndQuality(videoId, q.name());
                    return t.isPresent() && t.get().getStatus() == TaskStatus.PENDING;
                })
                .toList();
    }

    @Transactional
    public int reclaimStale(LocalDateTime threshold) {
        return taskRepo.reclaimStale(threshold);
    }

    @Transactional
    public int reclaimAllInProgress() {
        return taskRepo.reclaimAllInProgress();
    }

    public List<ProcessingTask> findPending() {
        return taskRepo.findByStatus(TaskStatus.PENDING);
    }

    public Map<Long, List<ProcessingTask>> findPendingGroupedByVideo() {
        return findPending().stream()
                .filter(t -> t.getUserId() != null && t.getFilePath() != null)
                .collect(Collectors.groupingBy(ProcessingTask::getVideoId));
    }

    public PendingSplit splitByPriority(List<ProcessingTask> tasks) {
        Set<String> names = tasks.stream()
                .map(ProcessingTask::getQuality)
                .collect(Collectors.toSet());

        List<Quality> fast = props.getFast().stream()
                .filter(q -> names.contains(q.name()))
                .toList();
        List<Quality> slow = props.getSlow().stream()
                .filter(q -> names.contains(q.name()))
                .toList();

        return new PendingSplit(fast, slow);
    }

    public record PendingSplit(List<Quality> fast, List<Quality> slow) {}

    @Transactional
    public int deletePendingByVideoId(Long videoId) {
        return taskRepo.deletePendingByVideoId(videoId);
    }

    @Transactional
    public int markCancelledByVideoId(Long videoId) {
        return taskRepo.markCancelledByVideoId(videoId, LocalDateTime.now());
    }

    public boolean isCancelled(Long videoId) {
        return taskRepo.isCancelled(videoId);
    }
}