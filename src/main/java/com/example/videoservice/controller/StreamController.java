package com.example.videoservice.controller;

import com.example.videoservice.model.Video;
import com.example.videoservice.model.VideoStatus;
import com.example.videoservice.service.StorageService;
import com.example.videoservice.service.VideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/stream")
@RequiredArgsConstructor
public class StreamController {

    private final VideoService videoService;
    private final StorageService storageService;

    private static final int URL_EXPIRY_SECONDS = 3600; // 1 час

    @GetMapping("/{videoId}/playlist-url")
    public ResponseEntity<?> getPlaylistUrl(@PathVariable Long videoId) {
        System.out.println("Request for playlist URL, videoId: {}" + videoId);
        Video video = videoService.getVideoById(videoId);
        System.out.println("Video status: {}, playlistPath: {}" + video.getStatus() + video.getPlaylistPath());
        if (video.getStatus() != VideoStatus.READY) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Video not ready yet"));
        }
        String playlistPath = video.getPlaylistPath();
        if (playlistPath == null || playlistPath.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Playlist not found"));
        }
        String url = storageService.getPublicUrl(playlistPath);
        System.out.println("Generated playlist URL: {}" + url);
        videoService.incrementViews(videoId);
        return ResponseEntity.ok(Map.of("playlistUrl", url));
    }
}