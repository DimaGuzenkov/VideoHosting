package com.example.uploadvideo.controller;

import com.example.uploadvideo.model.Video;
import com.example.uploadvideo.service.StorageService;
import com.example.uploadvideo.service.VideoService;
import com.example.uploadvideo.ValidationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/videos")
@RequiredArgsConstructor
public class VideoController {

    private final VideoService videoService;
    private final StorageService storageService;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadVideo(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam("description") String description,
            @RequestHeader("X-User-Id") Long userId) {

        ValidationResult validation = validateVideoUpload(file, title);
        if (!validation.isValid()) {
            return ResponseEntity.badRequest().body(Map.of("error", validation.getErrorMessage()));
        }

        try {
            String filePath = storageService.uploadFile(file, "videos/" + userId);
            Video video = videoService.createVideo(title, description, filePath, userId);

            return ResponseEntity.ok(Map.of(
                    "id", video.getId(),
                    "title", video.getTitle(),
                    "status", video.getStatus(),
                    "filePath", video.getFilePath()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Upload failed: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteVideo(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId) {

        Video video = videoService.getVideoById(id);
        // Проверяем, что видео принадлежит пользователю
        if (!video.getUserId().equals(userId)) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        String hlsPrefix = "videos/" + userId + "/" + video.getId() + "/hls/";
        storageService.deleteFilesWithPrefix(hlsPrefix);
        storageService.deleteFile(video.getFilePath());
        videoService.deleteVideo(id);
        return ResponseEntity.ok().build();
    }

    private ValidationResult validateVideoUpload(MultipartFile file, String title) {
        if (file.isEmpty()) {
            return ValidationResult.fail("File is empty");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("video/")) {
            return ValidationResult.fail("Only video files are allowed");
        }
        if (file.getSize() > 2_000_000_000L) {
            return ValidationResult.fail("File size exceeds 2GB limit");
        }
        if (title == null || title.trim().isEmpty()) {
            return ValidationResult.fail("Title is required");
        }
        return ValidationResult.success();
    }
}