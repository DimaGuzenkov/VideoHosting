package com.example.processor.processing;

import com.example.processor.ProcessorMode;
import com.example.processor.db.ProcessingTask;
import com.example.processor.db.ProcessingTaskRepository;
import com.example.processor.ffmpeg.Quality;
import com.example.processor.scheduler.data.TaskStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessingTaskRegistry {

    private final ProcessingTaskRepository repo;
    private final VideoProcessingProperties props;

    @Transactional
    public void persist(Long videoId, Long userId, String filePath,
                        ProcessorMode mode) {
        List<Quality> qualities = mode == ProcessorMode.FAST
                ? props.getFast()
                : props.getSlow();

        for (Quality q : qualities) {
            repo.insertIfNotExists(videoId, q.name(), userId, filePath);
        }
        log.debug("Persisted {} tasks for video {} [{}]",
                qualities.size(), videoId, mode);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<ProcessingTask> claimNextBatch(String workerId, ProcessorMode mode) {
        List<Quality> qualities = mode == ProcessorMode.FAST
                ? props.getFast()
                : props.getSlow();

        String[] qualityNames = qualities.stream()
                .map(Quality::name)
                .toArray(String[]::new);

        return repo.claimNextBatch(qualityNames, workerId);
    }

    @Transactional
    public void heartbeat(String workerId) {
        repo.updateHeartbeat(workerId, LocalDateTime.now());
    }

    @Transactional
    public void markDone(Long videoId, List<String> qualities) {
        repo.markDone(videoId, qualities, LocalDateTime.now());
    }

    @Transactional
    public void markFailed(Long videoId, List<String> qualities) {
        repo.markFailed(videoId, qualities, LocalDateTime.now());
    }

    @Transactional
    public int markCancelledByVideoId(Long videoId) {
        return repo.markCancelledByVideoId(videoId, LocalDateTime.now());
    }

    @Transactional
    public int deletePendingByVideoId(Long videoId) {
        return repo.deletePendingByVideoId(videoId);
    }

    @Transactional
    public int recoverStale(LocalDateTime threshold) {
        return repo.recoverStale(threshold);
    }

    @Transactional
    public int recoverByWorkerId(String workerId) {
        return repo.recoverByWorkerId(workerId);
    }

    public boolean isCancelled(Long videoId) {
        return repo.countCancelled(videoId) > 0;
    }

    public List<String> findDoneQualities(Long videoId) {
        return repo.findDoneQualities(videoId);
    }

    public List<ProcessingTask> findByVideoId(Long videoId) {
        return repo.findByVideoId(videoId);
    }

    @Transactional
    public int updateHeartbeatSafe(String workerId) {
        return repo.updateHeartbeat(workerId, LocalDateTime.now());
    }

    public Map<Long, List<ProcessingTask>> findPendingGroupedByVideo() {
        return repo.findByStatus(TaskStatus.PENDING).stream()
                .filter(t -> t.getUserId() != null && t.getFilePath() != null)
                .collect(Collectors.groupingBy(ProcessingTask::getVideoId));
    }
}