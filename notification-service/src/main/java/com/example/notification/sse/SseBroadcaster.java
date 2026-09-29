package com.example.notification.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@Slf4j
public class SseBroadcaster {

    private static final long SSE_TIMEOUT_MS = 30 * 60 * 1000L;

    private final Map<Long, List<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        subscribers.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> {
            log.debug("SSE completed for userId={}", userId);
            remove(userId, emitter);
        });
        emitter.onTimeout(() -> {
            log.debug("SSE timeout for userId={}", userId);
            remove(userId, emitter);
        });
        emitter.onError(e -> {
            log.debug("SSE error for userId={}: {}", userId, e.getMessage());
            remove(userId, emitter);
        });

        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (Exception ignored) {
            // Клиент отвалился сразу — не страшно
        }

        log.info("🔌 SSE subscriber added userId={}, total={}",
                userId, subscribers.get(userId).size());
        return emitter;
    }

    public void send(Long userId, String eventName, String payload) {
        List<SseEmitter> emitters = subscribers.get(userId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload));
            } catch (Exception e) {
                // НЕ вызываем emitter.complete() из Kafka-потока!
                // Tomcat сам закроет контекст, когда клиент отключится.
                log.warn("Failed to send SSE to userId={}, removing emitter: {}",
                        userId, e.getMessage());
                remove(userId, emitter);
            }
        }
    }

    private void remove(Long userId, SseEmitter emitter) {
        List<SseEmitter> list = subscribers.get(userId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                subscribers.remove(userId);
            }
        }
    }
}
