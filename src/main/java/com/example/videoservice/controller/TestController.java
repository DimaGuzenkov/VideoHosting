package com.example.videoservice.controller;

import com.example.videoservice.model.Video;
import com.example.videoservice.model.VideoStatus;
import com.example.videoservice.repository.VideoRepository;
import com.example.videoservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class TestController {

    private final UserRepository userRepository;
    private final VideoRepository videoRepository;

    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("pong");
    }

    @GetMapping("/videos")
    public ResponseEntity<List<Video>> getAllVideos() {
        return ResponseEntity.ok(videoRepository.findAll());
    }

    // Метод для быстрого создания тестового видео (потом удалим)
    @GetMapping("/create-demo")
    public ResponseEntity<Video> createDemo() {
        var user = userRepository.findByUsername("demo").orElse(null);
        if (user == null) {
            return ResponseEntity.badRequest().build();
        }
        Video video = Video.builder()
                .title("Demo video")
                .description("Auto created")
                .status(VideoStatus.UPLOADED)
                .user(user)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return ResponseEntity.ok(videoRepository.save(video));
    }
}