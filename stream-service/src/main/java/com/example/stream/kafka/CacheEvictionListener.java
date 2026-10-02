package com.example.stream.kafka;

import com.example.avro.VideoDeletedEvent;
import com.example.avro.VideoProcessedEvent;
import com.example.stream.redis.VideoCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CacheEvictionListener {
    private final VideoCacheService cache;

    @KafkaListener(
        topics = "video.processed",
        groupId = "stream-service-processed-cache"
    )
    public void onVideoProcessed(VideoProcessedEvent event) {
        Long videoId = event.getVideoId();
        Long userId = event.getUserId();
        log.info("🔄 Cache eviction (processed): videoId={}, userId={}", videoId, userId);
        cache.evictAllVideoLists();
    }

    @KafkaListener(
        topics = "video.deleted",
        groupId = "stream-service-deleted-cache"
    )
    public void onVideoDeleted(VideoDeletedEvent event) {
        Long videoId = event.getVideoId();
        log.info("🗑️ Cache eviction (deleted): videoId={}", videoId);
        cache.evictAllVideoLists();
        cache.evictVideo(videoId);
    }
}