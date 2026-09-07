package com.example.videoservice.controller;

import com.example.videoservice.model.Video;
import com.example.videoservice.model.User;
import com.example.videoservice.repository.UserRepository;
import com.example.videoservice.service.StorageService;
import com.example.videoservice.service.VideoService;
import com.example.videoservice.util.ValidationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/videos")
@RequiredArgsConstructor
public class VideoController {

    private final VideoService videoService;
    private final StorageService storageService;
    private final UserRepository userRepository;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadVideo(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam("description") String description) {

        // Валидация
        ValidationResult validation = validateVideoUpload(file, title);
        if (!validation.isValid()) {
            return ResponseEntity.badRequest().body(Map.of("error", validation.getErrorMessage()));
        }

        // Получаем текущего пользователя из контекста Security
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User user = userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        try {
            // Загружаем файл в MinIO
            String filePath = storageService.uploadFile(file, "videos/" + user.getId());

            // Сохраняем метаданные в БД
            Video video = videoService.createVideo(title, description, filePath, user.getId());

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

    // Вспомогательная валидация без исключений
    private ValidationResult validateVideoUpload(MultipartFile file, String title) {
        if (file.isEmpty()) {
            return ValidationResult.fail("File is empty");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("video/")) {
            return ValidationResult.fail("Only video files are allowed");
        }
        if (file.getSize() > 2_000_000_000L) { // 2GB
            return ValidationResult.fail("File size exceeds 2GB limit");
        }
        if (title == null || title.trim().isEmpty()) {
            return ValidationResult.fail("Title is required");
        }
        return ValidationResult.success();
    }

    @GetMapping
    public ResponseEntity<List<Video>> getUserVideos(Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        List<Video> videos = videoService.getVideosByUserId(user.getId());
        return ResponseEntity.ok(videos);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteVideo(@PathVariable Long id, Authentication authentication) {
        Video video = videoService.getVideoById(id);
        if (!video.getUser().getUsername().equals(authentication.getName())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        String hlsPrefix = "videos/" + video.getUser().getId() + "/" + video.getId() + "/hls/";
        storageService.deleteFilesWithPrefix(hlsPrefix);
        storageService.deleteFile(video.getFilePath());
        videoService.deleteVideo(id);
        return ResponseEntity.ok().build();
    }
}