package com.example.processor.kafka;

import com.example.avro.VideoUploadedEvent;
import com.example.processor.ProcessorMode;
import com.example.processor.processing.ProcessingTaskRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class VideoUploadedListener {

    private final ProcessingTaskRegistry registry;

    @Value("${processor.mode}")
    private ProcessorMode mode;

    @KafkaListener(
            topics = "video.uploaded",
            groupId = "${spring.kafka.consumer.group-id}",
            concurrency = "${spring.kafka.listener.concurrency:2}"
    )
    public void onVideoUploaded(VideoUploadedEvent event, Acknowledgment ack) {
        try {
            registry.persist(
                    event.getVideoId(),
                    event.getUserId(),
                    event.getFilePath().toString(),
                    mode);
            ack.acknowledge();
            log.info("📥 [{}] Persisted video {}",
                    mode, event.getVideoId());
        } catch (Exception e) {
            log.error("❌ [{}] Persist failed for video {}: {}",
                    mode, event.getVideoId(), e.getMessage(), e);
            // offset не коммитим — Kafka переотправит
        }
    }
}