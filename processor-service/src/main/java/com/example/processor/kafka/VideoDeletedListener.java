package com.example.processor.kafka;

import com.example.avro.VideoDeletedEvent;
import com.example.processor.ProcessorMode;
import com.example.processor.processing.ProcessingTaskRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class VideoDeletedListener {

    private final ProcessingTaskRegistry registry;

    @Value("${processor.mode}")
    private ProcessorMode mode;

    @KafkaListener(
            topics = "video.deleted",
            groupId = "${spring.kafka.consumer.deleted-group-id}"
    )
    public void onVideoDeleted(VideoDeletedEvent event) {
        Long videoId = event.getVideoId();
        log.info("🗑️ [{}] video.deleted: {}", mode, videoId);

        int deleted = registry.deletePendingByVideoId(videoId);
        int cancelled = registry.markCancelledByVideoId(videoId);

        log.info("🗑️ [{}] video {}: {} pending deleted, {} in-progress cancelled",
                mode, videoId, deleted, cancelled);
    }
}