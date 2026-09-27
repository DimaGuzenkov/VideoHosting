package com.example.processor.kafka;

import com.example.avro.VideoUploadedEvent;
import com.example.processor.processing.VideoProcessingService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VideoProcessingConsumer {

    private final VideoProcessingService processingService;

    @KafkaListener(topics = "video.uploaded", groupId = "video-processor-group")
    public void onVideoUploaded(VideoUploadedEvent event) {
        processingService.process(event);
    }
}