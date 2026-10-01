package com.example.processor.processing;

import com.example.processor.db.ProcessingTask;
import com.example.processor.ffmpeg.FfmpegService;
import com.example.processor.ffmpeg.HlsPlaylistBuilder;
import com.example.processor.ffmpeg.Quality;
import com.example.processor.kafka.EventPublisher;
import com.example.processor.storage.StorageService;
import com.example.processor.storage.TempDir;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class VideoProcessingService {

    private final StorageService storage;
    private final FfmpegService ffmpeg;
    private final HlsPlaylistBuilder playlistBuilder;
    private final EventPublisher eventPublisher;
    private final ProcessingTaskRegistry registry;
    private final VideoProcessingProperties props;

    /**
     * Обрабатывает batch задач одного видео (все PENDING для этого видео).
     * Для FAST — 1 качество (360p), для SLOW — до 4 качеств.
     */
    public void process(List<ProcessingTask> batch, String workerId) {
        if (batch.isEmpty()) return;

        ProcessingTask first = batch.get(0);
        Long videoId = first.getVideoId();
        Long userId = first.getUserId();
        String filePath = first.getFilePath();

        List<String> qualityNames = batch.stream()
                .map(ProcessingTask::getQuality)
                .toList();

        List<Quality> qualities = props.getAllQualities().stream()
                .filter(q -> qualityNames.contains(q.name()))
                .toList();

        try (TempDir temp = new TempDir()) {
            Path input = download(filePath, temp);
            Path hlsDir = temp.createSubdir("hls");

            ffmpeg.convertToHls(input, hlsDir, qualities);

            if (registry.isCancelled(videoId)) {
                log.info("🗑️ [post-ffmpeg] videoId={} cancelled, skip upload", videoId);
                return;
            }

            String basePath = "videos/" + userId + "/" + videoId + "/hls/";
            storage.uploadDirectory(hlsDir, basePath);

            registry.markDone(videoId, qualityNames);
            updateMasterPlaylist(videoId, basePath);

            eventPublisher.publishProcessed(videoId, userId, "READY",
                    basePath + "master.m3u8");

            log.info("🎉 videoId={} processed: {}", videoId, qualityNames);

        } catch (Exception e) {
            if (registry.isCancelled(videoId)) {
                log.info("🗑️ [catch] videoId={} cancelled, skip FAILED", videoId);
                return;
            }
            registry.markFailed(videoId, qualityNames);
            eventPublisher.publishProcessed(videoId, userId, "FAILED", null);
            throw new RuntimeException("Processing failed: " + e.getMessage(), e);
        }
    }

    private void updateMasterPlaylist(Long videoId, String basePath) throws Exception {
        List<String> doneNames = registry.findDoneQualities(videoId);
        List<Quality> done = props.getAllQualities().stream()
                .filter(q -> doneNames.contains(q.name()))
                .toList();

        if (done.isEmpty()) {
            log.warn("⚠️ No DONE qualities for videoId={}, skip master", videoId);
            return;
        }

        Path tmpDir = Files.createTempDirectory("master_" + videoId + "_");
        playlistBuilder.writeMasterPlaylist(tmpDir, done);
        Path master = tmpDir.resolve("master.m3u8");

        try (InputStream in = Files.newInputStream(master)) {
            storage.uploadFile(master, basePath + "master.m3u8");
        }

        log.info("📺 Updated master for videoId={}: {}",
                videoId, done.stream().map(Quality::name).toList());
    }

    private Path download(String filePath, TempDir temp) throws Exception {
        Path input = temp.resolve("input.mp4");
        try (InputStream in = storage.downloadFile(filePath);
             OutputStream out = Files.newOutputStream(input)) {
            in.transferTo(out);
        }
        return input;
    }
}