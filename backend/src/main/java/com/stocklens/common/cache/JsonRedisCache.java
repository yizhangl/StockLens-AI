package com.stocklens.common.cache;

import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Best-effort JSON cache: Redis failures always fall through to PostgreSQL/provider paths. */
@Component
public class JsonRedisCache {
    private static final Logger log = LoggerFactory.getLogger(JsonRedisCache.class);
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    public JsonRedisCache(StringRedisTemplate redis, ObjectMapper objectMapper) { this.redis = redis; this.objectMapper = objectMapper; }
    public <T> Optional<T> get(String key, Class<T> type) {
        try { String value = redis.opsForValue().get(key); return value == null ? Optional.empty() : Optional.of(objectMapper.readValue(value, type)); }
        catch (Exception exception) { log.warn("Redis cache read failed for key={}; using durable fallback", key, exception); return Optional.empty(); }
    }
    public void put(String key, Object value, Duration ttl) {
        try { redis.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl); }
        catch (Exception exception) { log.warn("Redis cache write failed for key={}; continuing without cache", key, exception); }
    }
    public void evict(String key) { try { redis.delete(key); } catch (Exception exception) { log.warn("Redis cache eviction failed for key={}", key, exception); } }
    /** Uses SCAN, never Redis KEYS. */
    public void evictByPrefix(String prefix) {
        try (Cursor<String> cursor = redis.scan(ScanOptions.scanOptions().match(prefix + "*").count(100).build())) {
            while (cursor.hasNext()) redis.delete(cursor.next());
        } catch (Exception exception) { log.warn("Redis namespace eviction failed for prefix={}; continuing without cache", prefix, exception); }
    }
}
