package com.example.notification.kafka;

import com.example.avro.VideoProcessedEvent;
import com.example.notification.sse.SseBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class VideoProcessedListener {

    private final SseBroadcaster broadcaster;

    @Value("${spring.application.name}")
    private String serviceName;

    @KafkaListener(
            topics = "video.processed",
            groupId = "#{T(java.util.UUID).randomUUID().toString()}"
    )
    public void onVideoProcessed(VideoProcessedEvent event) {
        Long userId = event.getUserId();
        Long videoId = event.getVideoId();
        String status = event.getStatus().toString();

        log.info("📥 video.processed: userId={}, videoId={}, status={}", userId, videoId, status);

        String payload = String.format(
                "{\"videoId\":%d,\"status\":\"%s\"}", videoId, status
        );
        broadcaster.send(userId, "video-status", payload);
    }
}