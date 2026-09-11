package com.example.stream.controller;

import com.example.stream.model.Video;
import com.example.stream.service.VideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/videos")
@RequiredArgsConstructor
public class VideoController {

    private final VideoService videoService;

    @GetMapping
    public ResponseEntity<List<Video>> getUserVideos(@RequestHeader("X-User-Id") Long userId) {
        List<Video> videos = videoService.getVideosByUserId(userId);
        return ResponseEntity.ok(videos);
    }
}