package com.factory.central.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

@Tag("SF-M1.3")
@ExtendWith(MockitoExtension.class)
class LatestTagCacheRemoveTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private SetOperations<String, String> sets;

    private InMemoryLatestTagCache memory;

    @BeforeEach
    void setUp() {
        memory = new InMemoryLatestTagCache();
    }

    @Test
    void memoryRemoveDropsLatestAndAlarm() {
        memory.put(sample("INDORE", "LINE-01", "VALVE_101_POS"));
        memory.put(sample("PUNE", "LINE-01", "VALVE_101_POS"));

        memory.remove("INDORE", "LINE-01", "VALVE_101_POS");

        assertThat(memory.get("INDORE", "LINE-01", "VALVE_101_POS")).isEmpty();
        assertThat(memory.get("PUNE", "LINE-01", "VALVE_101_POS")).isPresent();
        assertThat(memory.findAlarms("INDORE", "LINE-01")).isEmpty();
    }

    @Test
    void redisRemoveDeletesValueAndIndex() {
        when(redis.opsForSet()).thenReturn(sets);
        RedisLatestTagCache cache = new RedisLatestTagCache(redis, new com.fasterxml.jackson.databind.ObjectMapper());

        cache.remove("INDORE", "LINE-01", "VALVE_101_POS");

        verify(redis).delete("telemetry:latest:INDORE|LINE-01|VALVE_101_POS");
        verify(sets).remove("telemetry:latest:index", "telemetry:latest:INDORE|LINE-01|VALVE_101_POS");
    }

    private static LatestTagState sample(String siteId, String lineId, String tagCode) {
        return LatestTagState.initial(
                siteId,
                lineId,
                tagCode,
                90.0,
                "GOOD",
                Instant.parse("2026-11-21T00:00:00Z"),
                AlarmSeverity.CRITICAL,
                AlarmDirection.HIGH);
    }
}
