package com.example.uploadvideo.kafka;

//import com.example.uploadvideo.VideoUploadedEvent;
import com.example.avro.VideoDeletedEvent;
import com.example.avro.VideoUploadedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String Msg = "✅ Event published to Kafka for video ID: {}";

    public void publishVideoUploaded(VideoUploadedEvent event) {
        kafkaTemplate.send(KafkaConfig.VIDEO_UPLOADED_TOPIC, event);
        log.info(Msg, event.getVideoId());
    }


    public void publishVideoDeleted(VideoDeletedEvent event) {
        kafkaTemplate.send(KafkaConfig.VIDEO_DELETED_TOPIC, event);
        log.info(Msg, event.getVideoId());
    }
}