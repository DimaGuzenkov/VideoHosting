package com.example.stream.controller;

import com.example.stream.model.Video;
import com.example.stream.model.VideoStatus;
import com.example.stream.service.StorageService;
import com.example.stream.service.VideoService;
import com.example.stream.service.ViewCounterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/stream")
@RequiredArgsConstructor
@Slf4j
public class StreamController {

    private final VideoService videoService;
    private final StorageService storageService;
    private final ViewCounterService viewCounterService;

    private static final int URL_EXPIRY_SECONDS = 3600;

    @GetMapping("/{videoId}/playlist-url")
    public ResponseEntity<?> getPlaylistUrl(@PathVariable Long videoId) {
//        log.info("Request for playlist URL, videoId: {}", videoId);

        Video video = videoService.getVideoById(videoId);
        if (video.getStatus() != VideoStatus.READY) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Video not ready yet"));
        }

        String playlistPath = video.getPlaylistPath();
        if (playlistPath == null || playlistPath.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Playlist not found"));
        }

        viewCounterService.increment(videoId);

        String url = storageService.getPublicUrl(playlistPath);
        return ResponseEntity.ok(Map.of("playlistUrl", url));
    }
}