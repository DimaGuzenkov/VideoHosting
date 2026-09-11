package com.example.uploadvideo.service;

import com.example.uploadvideo.config.KafkaConfig;
//import com.example.uploadvideo.VideoUploadedEvent;
import com.example.avro.VideoUploadedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishVideoUploaded(VideoUploadedEvent event) {
        kafkaTemplate.send(KafkaConfig.VIDEO_UPLOADED_TOPIC, event);
        System.out.println("✅ Event published to Kafka for video ID: " + event.getVideoId());
    }
}