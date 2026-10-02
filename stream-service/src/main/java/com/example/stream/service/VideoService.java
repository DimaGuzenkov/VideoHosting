package com.example.stream.service;

import com.example.stream.redis.VideoCacheService;
import com.example.stream.model.Video;
import com.example.stream.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class VideoService {

    private final VideoRepository videoRepository;
    private final VideoCacheService cache;

    public List<Video> getVideosByUserId(Long userId) {
        return cache.getVideoList(userId,
                () -> {
                    log.info("🎯 Go to DB videoList:{}", userId);
                    return videoRepository.findAllByUserId(userId);
                });
    }

    public Video getVideoById(Long id) {
        return cache.getVideo(id,
                () -> videoRepository.findById(id)
                        .orElseThrow(() -> new RuntimeException("Video not found")));
    }
}