package com.example.processor.processing;

import com.example.avro.VideoUploadedEvent;
import com.example.processor.ffmpeg.FfmpegService;
import com.example.processor.ffmpeg.HlsPlaylistBuilder;
import com.example.processor.ffmpeg.Quality;
import com.example.processor.kafka.EventPublisher;
import com.example.processor.scheduler.ProcessingTaskRepository;
import com.example.processor.scheduler.TaskStatus;
import com.example.processor.scheduler.VideoTask;
import com.example.processor.storage.StorageService;
import com.example.processor.storage.TempDir;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class VideoProcessingService {

    private final ProcessingTaskService taskService;
    private final StorageService storage;
    private final FfmpegService ffmpeg;
    private final HlsPlaylistBuilder playlistBuilder;
    private final EventPublisher eventPublisher;

    public void process(VideoTask task) {
        Long videoId = task.event().getVideoId();

        List<Quality> claimed = taskService.claim(videoId, task.qualities());
        if (claimed.isEmpty()) {
            log.info("⏭ All qualities already claimed");
            return;
        }

        try (TempDir temp = new TempDir()) {
            Path input = download(task.event(), temp);
            Path hlsDir = temp.createSubdir("hls");
            ffmpeg.convertToHls(input, hlsDir, claimed);
            playlistBuilder.writeMasterPlaylist(hlsDir, claimed);

            String basePath = "videos/" + task.event().getUserId() + "/" + videoId + "/hls/";
            storage.uploadDirectory(hlsDir, basePath);

            taskService.markDone(videoId, claimed);
            eventPublisher.publishProcessed(videoId, "READY", basePath + "master.m3u8");
            log.info("🎉 videoId={} processed, qualities={}", videoId, claimed);

        } catch (Exception e) {
            taskService.markFailed(videoId, claimed);
            eventPublisher.publishProcessed(videoId, "FAILED", null);
            throw new RuntimeException("Processing failed: " + e.getMessage(), e);
        }
    }

    private Path download(VideoUploadedEvent event, TempDir temp) throws IOException {
        Path input = temp.resolve("input.mp4");
        try (InputStream in = storage.downloadFile(event.getFilePath().toString());
             OutputStream out = Files.newOutputStream(input)) {
            in.transferTo(out);
        }
        return input;
    }
}
