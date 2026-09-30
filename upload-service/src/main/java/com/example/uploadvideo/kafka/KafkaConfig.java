package com.example.uploadvideo.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    public static final String VIDEO_UPLOADED_TOPIC = "video.uploaded";
    public static final String VIDEO_DELETED_TOPIC = "video.deleted";

    @Bean
    public NewTopic videoUploadedTopic() {
        return TopicBuilder.name(VIDEO_UPLOADED_TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
    }
}