package com.example.processor.kafka;

import com.example.avro.VideoUploadedEvent;
import com.example.processor.scheduler.VideoTaskScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class VideoProcessingListener {

    private final VideoTaskScheduler scheduler;

    @KafkaListener(topics = "video.uploaded", groupId = "video-processor-group")
    public void onVideoUploaded(VideoUploadedEvent event, Acknowledgment ack) {
        try {
            scheduler.submit(event);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to submit task for videoId={}: {}",
                    event.getVideoId(), e.getMessage(), e);
        }
    }
}