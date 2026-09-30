package com.example.uploadvideo.upload;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class UploadSessionService {

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final String KEY = "upload:session:";
    private static final Duration TTL = Duration.ofHours(24);

    public record Session(
            Long userId, String objectKey, String title, String description,
            String fileName, long fileSize, String contentType
    ) {}

    public void save(String uploadId, Session session) {
        try {
            redis.opsForValue().set(KEY + uploadId, mapper.writeValueAsString(session), TTL);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save session: " + e.getMessage(), e);
        }
    }

    public Session get(String uploadId) {
        try {
            String json = redis.opsForValue().get(KEY + uploadId);
            if (json == null) return null;
            return mapper.readValue(json, Session.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load session: " + e.getMessage(), e);
        }
    }

    public void delete(String uploadId) {
        redis.delete(KEY + uploadId);
    }
}