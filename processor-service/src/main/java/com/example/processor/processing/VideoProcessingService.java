package com.example.processor.processing;

import com.example.avro.VideoUploadedEvent;
import com.example.processor.ffmpeg.FfmpegService;
import com.example.processor.ffmpeg.HlsPlaylistBuilder;
import com.example.processor.kafka.EventPublisher;
import com.example.processor.storage.StorageService;
import com.example.processor.storage.TempDir;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;

@Service
@RequiredArgsConstructor
@Slf4j
public class VideoProcessingService {

    private final StorageService storage;
    private final FfmpegService ffmpeg;
    private final HlsPlaylistBuilder playlistBuilder;
    private final EventPublisher eventPublisher;
    private final VideoProcessingProperties props;

    public void process(VideoUploadedEvent event) {
        Long videoId = event.getVideoId();
        log.info("📥 Start processing videoId={}", videoId);

        try (var temp = new TempDir()) {
            var input = temp.resolve("input.mp4");
            try (InputStream in = storage.downloadFile(event.getFilePath().toString());
                 OutputStream out = Files.newOutputStream(input)) {
                in.transferTo(out);
            }
            log.info("⬇️ Downloaded video {} to {}", videoId, input);

            var hlsDir = temp.createSubdir("hls");
            var qualities = props.getSlow();
            qualities.add(props.getFast().get(0));
            ffmpeg.convertToHls(input, hlsDir, qualities);

            playlistBuilder.writeMasterPlaylist(hlsDir, qualities);

            var basePath = "videos/" + event.getUserId() + "/" + videoId + "/hls/";
            storage.uploadDirectory(hlsDir, basePath);
            log.info("☁️ Uploaded HLS segments to {}", basePath);

            eventPublisher.publishProcessed(videoId, event.getUserId(), "READY", basePath + "master.m3u8");
            log.info("🎉 Processing finished videoId={}", videoId);
        } catch (Exception e) {
            log.error("❌ Processing failed for videoId={}: {}", videoId, e.getMessage(), e);
            eventPublisher.publishProcessed(videoId, event.getUserId(), "FAILED", null);
        }
    }
}
