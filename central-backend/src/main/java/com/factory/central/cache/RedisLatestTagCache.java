package com.factory.central.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisLatestTagCache implements LatestTagCache {

    private static final String KEY_PREFIX = "telemetry:latest:";
    private static final String INDEX_KEY = "telemetry:latest:index";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisLatestTagCache(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public void put(LatestTagState state) {
        String key = KEY_PREFIX + InMemoryLatestTagCache.key(state.siteId(), state.lineId(), state.tagCode());
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(state));
            redis.opsForSet().add(INDEX_KEY, key);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize latest tag state", ex);
        }
    }

    @Override
    public void remove(String siteId, String lineId, String tagCode) {
        String key = KEY_PREFIX + InMemoryLatestTagCache.key(siteId, lineId, tagCode);
        redis.delete(key);
        redis.opsForSet().remove(INDEX_KEY, key);
    }

    @Override
    public Optional<LatestTagState> get(String siteId, String lineId, String tagCode) {
        String json = redis.opsForValue().get(KEY_PREFIX + InMemoryLatestTagCache.key(siteId, lineId, tagCode));
        return Optional.ofNullable(read(json));
    }

    @Override
    public List<LatestTagState> find(String siteId, String lineId, String tagCode) {
        Set<String> keys = redis.opsForSet().members(INDEX_KEY);
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        List<LatestTagState> matches = new ArrayList<>();
        for (String key : keys) {
            LatestTagState state = read(redis.opsForValue().get(key));
            if (state != null && matchesFilter(state, siteId, lineId, tagCode)) {
                matches.add(state);
            }
        }
        matches.sort((a, b) -> {
            int site = a.siteId().compareTo(b.siteId());
            if (site != 0) {
                return site;
            }
            int line = a.lineId().compareTo(b.lineId());
            if (line != 0) {
                return line;
            }
            return a.tagCode().compareTo(b.tagCode());
        });
        return matches;
    }

    @Override
    public List<LatestTagState> findAlarms(String siteId, String lineId) {
        return find(siteId, lineId, null).stream()
                .filter(state -> state.severity() != AlarmSeverity.NONE)
                .toList();
    }

    private LatestTagState read(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, LatestTagState.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to deserialize latest tag state", ex);
        }
    }

    private static boolean matchesFilter(LatestTagState state, String siteId, String lineId, String tagCode) {
        if (siteId != null && !siteId.equals(state.siteId())) {
            return false;
        }
        if (lineId != null && !lineId.equals(state.lineId())) {
            return false;
        }
        return tagCode == null || tagCode.equals(state.tagCode());
    }
}
