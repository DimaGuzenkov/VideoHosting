package com.example.processor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class VideoProcessorApplication {
    public static void main(String[] args) {
        SpringApplication.run(VideoProcessorApplication.class, args);
    }
}
