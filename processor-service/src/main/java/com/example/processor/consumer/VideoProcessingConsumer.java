package com.example.processor.consumer;

import com.example.processor.client.StreamServiceClient;
import com.example.processor.event.VideoUploadedEvent;
import com.example.processor.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class VideoProcessingConsumer {

    private final StorageService storageService;
    private final StreamServiceClient streamServiceClient;

    @KafkaListener(topics = "video-uploaded", groupId = "video-processor-group")
    public void processVideo(String rawJson) {
        log.info("Raw JSON: {}", rawJson);
//        Long videoId = event.getVideoId();
//        log.info("📥 Received processing event for video ID: {}", videoId);
//
//        try {
//            // 1. Скачиваем видео из MinIO
//            String tempDir = System.getProperty("java.io.tmpdir") + "/video_" + UUID.randomUUID();
//            Files.createDirectories(Paths.get(tempDir));
//            Path inputPath = Paths.get(tempDir, "input.mp4");
//            try (InputStream in = storageService.downloadFile(event.getFilePath());
//                 FileOutputStream out = new FileOutputStream(inputPath.toFile())) {
//                in.transferTo(out);
//            }
//            log.info("📥 Video downloaded to: {}", inputPath);
//
//            // 2. Конвертируем в HLS
//            String outputDir = tempDir + "/hls";
//            Files.createDirectories(Paths.get(outputDir));
//
//            String os = System.getProperty("os.name").toLowerCase();
//            String shell = os.contains("win") ? "cmd" : "sh";
//            String shellArg = os.contains("win") ? "/c" : "-c";
//
//            String cmd720 = String.format(
//                    "ffmpeg -i %s -profile:v baseline -level 3.0 -s 1280x720 -start_number 0 -hls_time 10 -hls_list_size 0 -f hls %s/playlist_720.m3u8",
//                    inputPath, outputDir
//            );
//            String cmd360 = String.format(
//                    "ffmpeg -i %s -profile:v baseline -level 3.0 -s 640x360 -start_number 0 -hls_time 10 -hls_list_size 0 -f hls %s/playlist_360.m3u8",
//                    inputPath, outputDir
//            );
//
//            ProcessBuilder pb720 = new ProcessBuilder(shell, shellArg, cmd720);
//            pb720.redirectErrorStream(true);
//            Process process720 = pb720.start();
//            int exit720 = process720.waitFor();
//            if (exit720 != 0) {
//                throw new RuntimeException("FFmpeg 720p failed with code " + exit720);
//            }
//            log.info("✅ FFmpeg 720p completed");
//
//            ProcessBuilder pb360 = new ProcessBuilder(shell, shellArg, cmd360);
//            pb360.redirectErrorStream(true);
//            Process process360 = pb360.start();
//            int exit360 = process360.waitFor();
//            if (exit360 != 0) {
//                throw new RuntimeException("FFmpeg 360p failed with code " + exit360);
//            }
//            log.info("✅ FFmpeg 360p completed");
//
//            // Мастер-плейлист
//            String masterContent = "#EXTM3U\n" +
//                    "#EXT-X-STREAM-INF:BANDWIDTH=2000000,RESOLUTION=1280x720\n" +
//                    "playlist_720.m3u8\n" +
//                    "#EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360\n" +
//                    "playlist_360.m3u8\n";
//            Files.write(Paths.get(outputDir, "master.m3u8"), masterContent.getBytes());
//
//            log.info("✅ HLS conversion completed for video ID: {}", videoId);
//
//            // 3. Загружаем сегменты в MinIO
//            String basePath = "videos/" + event.getUserId() + "/" + videoId + "/hls/";
//            Files.walk(Paths.get(outputDir))
//                    .filter(Files::isRegularFile)
//                    .forEach(file -> {
//                        try {
//                            String objectName = basePath + file.getFileName().toString();
//                            try (InputStream is = Files.newInputStream(file)) {
//                                storageService.uploadFile(is, file.toFile().length(), objectName,
//                                        file.getFileName().toString().endsWith(".m3u8") ?
//                                                "application/vnd.apple.mpegurl" : "video/MP2T");
//                            }
//                        } catch (Exception e) {
//                            log.error("Error uploading HLS file: {}", e.getMessage());
//                        }
//                    });
//
//            log.info("✅ All segments uploaded, updating status...");
//            streamServiceClient.updateStatus(videoId, "READY", basePath + "master.m3u8");
//
//            // 4. Удаляем временные файлы
//            Files.walk(Paths.get(tempDir))
//                    .sorted((a, b) -> -a.compareTo(b))
//                    .forEach(path -> {
//                        try {
//                            Files.deleteIfExists(path);
//                        } catch (Exception e) {
//                            log.warn("Could not delete temp file: {}", path);
//                        }
//                    });
//
//            log.info("🎉 Video processing finished for ID: {}", videoId);
//
//        } catch (Exception e) {
//            log.error("❌ Error processing video {}: {}", videoId, e.getMessage(), e);
//            streamServiceClient.updateStatus(videoId, "FAILED", null);
//        }
    }
}