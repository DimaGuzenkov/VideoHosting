package com.example.stream.redis;

import com.example.stream.model.Video;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class VideoCacheService {

    private final RedisTemplate<String, Object> redis;
    private final RedissonClient redisson;

    @Value("${cache.video-list-ttl-seconds:30}")
    private int videoListTtl;

    @Value("${cache.video-ttl-seconds:60}")
    private int videoTtl;

    private static final String LIST_PREFIX = "videoList:";
    private static final String VIDEO_PREFIX = "video:";
    private static final String LOCK_SUFFIX = ":lock";
    private static final long LOCK_WAIT_MS = 2000;
    private static final long LOCK_LEASE_SEC = 10;

    // ============================================================
    // getVideoList — с distribution lock и double-check
    // ============================================================

    @SuppressWarnings("unchecked")
    public List<Video> getVideoList(Long userId, Supplier<List<Video>> loader) {
        String key = LIST_PREFIX + userId;

        // 1. Fast path: проверяем кеш
        Object cached = redis.opsForValue().get(key);
        if (cached instanceof List<?>) {
//            log.info("🎯 CACHE HIT videoList:{}", userId);
            return (List<Video>) cached;
        }
        log.info("🎯 CACHE MISS videoList:{}", userId);

        // 2. MISS — берём распределённый лок
        RLock lock = redisson.getLock(key + LOCK_SUFFIX);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(LOCK_WAIT_MS, LOCK_LEASE_SEC * 1000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Lock interrupted for {}, fallback to DB", key);
            return loader.get();
        }

        if (!acquired) {
            // Не смогли взять лок — другой инстанс грузит.
            // Ждём и пробуем кеш ещё раз.
            log.debug("⏳ Lock busy for {}, waiting...", key);
            for (int i = 0; i < 20; i++) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                cached = redis.opsForValue().get(key);
                if (cached instanceof List<?>) {
                    log.debug("🎯 CACHE HIT after wait videoList:{}", userId);
                    return (List<Video>) cached;
                }
            }
            // Таймаут — degraded mode: читаем из БД
            log.warn("⚠️ Lock timeout for {}, fallback to DB", key);
            return loader.get();
        }

        try {
            // 3. Double-check: пока ждали лок, кто-то мог загрузить
            cached = redis.opsForValue().get(key);
            if (cached instanceof List<?>) {
                log.debug("🎯 CACHE HIT after lock videoList:{}", userId);
                return (List<Video>) cached;
            }

            // 4. Мы — единственные, кто грузит
            log.debug("💾 CACHE MISS videoList:{}, loading from DB", userId);
            List<Video> loaded = loader.get();
            redis.opsForValue().set(key, loaded, Duration.ofSeconds(videoListTtl));
            return loaded;

        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    // ============================================================
    // getVideo — то же самое для одного видео
    // ============================================================

    public Video getVideo(Long videoId, Supplier<Video> loader) {
        String key = VIDEO_PREFIX + videoId;

        Object cached = redis.opsForValue().get(key);
        if (cached instanceof Video v) {
            log.debug("🎯 CACHE HIT video:{}", videoId);
            return v;
        }

        RLock lock = redisson.getLock(key + LOCK_SUFFIX);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(LOCK_WAIT_MS, LOCK_LEASE_SEC * 1000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return loader.get();
        }

        if (!acquired) {
            for (int i = 0; i < 20; i++) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                cached = redis.opsForValue().get(key);
                if (cached instanceof Video v) {
                    return v;
                }
            }
            return loader.get();
        }

        try {
            cached = redis.opsForValue().get(key);
            if (cached instanceof Video v) {
                return v;
            }

            Video loaded = loader.get();
            redis.opsForValue().set(key, loaded, Duration.ofSeconds(videoTtl));
            return loaded;

        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    public void evictAllVideoLists() {
        Set<String> keys = new HashSet<>();
        ScanOptions options = ScanOptions.scanOptions()
                .match(LIST_PREFIX + "*")
                .count(100)
                .build();

        try (Cursor<String> cursor = redis.scan(options)) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        } catch (Exception e) {
            log.warn("Failed to scan video list keys: {}", e.getMessage());
            return;
        }

        if (!keys.isEmpty()) {
            redis.delete(keys);
            log.info("🗑️ Evicted {} video list keys", keys.size());
        }
    }

    public void evictVideo(Long videoId) {
        redis.delete(VIDEO_PREFIX + videoId);
        log.debug("🗑️ Evicted video:{}", videoId);
    }

    public void clearAll() {
        var keys = redis.keys("video*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
            log.info("🧹 Cleared {} cache keys", keys.size());
        }
    }
}