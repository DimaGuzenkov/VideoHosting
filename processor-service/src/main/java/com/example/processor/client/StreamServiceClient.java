package com.example.processor.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class StreamServiceClient {

    private final WebClient webClient;

    @Value("${stream.service.url}")
    private String streamServiceUrl;

    public void updateStatus(Long videoId, String status, String playlistPath) {
        try {
            String url = streamServiceUrl + "/internal/videos/" + videoId + "/status";
            Map<String, Object> body = Map.of("status", status, "playlistPath", playlistPath);

            webClient.patch()
                    .uri(url)
                    .bodyValue(body)
                    .retrieve()
                    .toBodilessEntity()
                    .block();  // блокирующий вызов для синхронного использования

            log.info("✅ Updated status for video {} to {}", videoId, status);
        } catch (Exception e) {
            log.error("❌ Failed to update status for video {}: {}", videoId, e.getMessage());
        }
    }
}