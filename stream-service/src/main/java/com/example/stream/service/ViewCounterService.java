package com.example.stream.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ViewCounterService {

    private final StringRedisTemplate redis;
    private static final String KEY_PREFIX = "video:views:";

    public void increment(Long videoId) {
        redis.opsForValue().increment(KEY_PREFIX + videoId);
    }

    public Map<Long, Long> drainAll() {
        Set<String> keys = redis.keys(KEY_PREFIX + "*");
        if (keys.isEmpty()) return Map.of();

        Map<Long, Long> result = new HashMap<>();
        for (String key : keys) {
            String value = redis.opsForValue().getAndDelete(key);
            if (value != null) {
                Long videoId = Long.parseLong(key.substring(KEY_PREFIX.length()));
                result.put(videoId, Long.parseLong(value));
            }
        }
        return result;
    }
}
