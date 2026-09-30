package com.example.processor.kafka;

import com.example.avro.VideoProcessedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String TOPIC = "video.processed";

    public void publishProcessed(Long videoId, Long userId, String status, String playlistPath) {
        VideoProcessedEvent event = VideoProcessedEvent.newBuilder()
                .setVideoId(videoId)
                .setUserId(userId)
                .setStatus(status)
                .setPlaylistPath(playlistPath)
                .build();

        kafkaTemplate.send(TOPIC, event);
        log.info("📤 Published {}: videoId={}", TOPIC, videoId);
    }
}
