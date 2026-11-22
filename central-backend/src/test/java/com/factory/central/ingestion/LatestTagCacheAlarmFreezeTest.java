package com.factory.central.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.factory.central.cache.AlarmSeverity;
import com.factory.central.cache.InMemoryLatestTagCache;
import com.factory.central.cache.LatestTagState;
import com.factory.central.cache.NoOpTelemetryStreamSink;
import com.factory.central.registry.Protocol;
import com.factory.central.registry.TagDataType;
import com.factory.central.registry.TagMapping;
import com.factory.central.registry.TagMappingRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("SF-M1.4")
class LatestTagCacheAlarmFreezeTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private TagMappingRepository tagMappingRepository;
    private InMemoryLatestTagCache cache;
    private TelemetryIngestService ingestService;
    private TelemetryBatchWriter batchWriter;

    @BeforeEach
    void setUp() {
        tagMappingRepository = mock(TagMappingRepository.class);
        cache = new InMemoryLatestTagCache();
        TelemetryHistoryRepository historyRepository = mock(TelemetryHistoryRepository.class);
        TelemetryProperties properties = new TelemetryProperties();
        batchWriter = new TelemetryBatchWriter(historyRepository, properties, new SimpleMeterRegistry());
        Clock clock = Clock.fixed(Instant.parse("2026-11-19T12:00:00Z"), ZoneOffset.UTC);
        ingestService = new TelemetryIngestService(
                tagMappingRepository,
                new AlarmEvaluator(),
                cache,
                batchWriter,
                NoOpTelemetryStreamSink.INSTANCE,
                clock);

        TagMapping tag = tempTag();
        when(tagMappingRepository.findBySiteIdAndLineIdAndTagCode("INDORE", "LINE-01", "REACTOR_TEMP_C"))
                .thenReturn(Optional.of(tag));
    }

    @Test
    void badQualityAfterCriticalFreezesAlarmUntilGoodClears() {
        ingest(85.0, "GOOD");
        LatestTagState critical = cache.get("INDORE", "LINE-01", "REACTOR_TEMP_C").orElseThrow();
        assertThat(critical.severity()).isEqualTo(AlarmSeverity.CRITICAL);
        assertThat(critical.alarmFrozen()).isFalse();

        ingest(40.0, "BAD");
        LatestTagState frozen = cache.get("INDORE", "LINE-01", "REACTOR_TEMP_C").orElseThrow();
        assertThat(frozen.severity()).isEqualTo(AlarmSeverity.CRITICAL);
        assertThat(frozen.alarmFrozen()).isTrue();
        assertThat(frozen.value()).isEqualTo(40.0);
        assertThat(frozen.quality()).isEqualTo("BAD");
        assertThat(cache.findAlarms("INDORE", "LINE-01")).hasSize(1);

        ingest(40.0, "UNCERTAIN");
        LatestTagState stillFrozen = cache.get("INDORE", "LINE-01", "REACTOR_TEMP_C").orElseThrow();
        assertThat(stillFrozen.severity()).isEqualTo(AlarmSeverity.CRITICAL);
        assertThat(stillFrozen.alarmFrozen()).isTrue();

        ingest(50.0, "GOOD");
        LatestTagState cleared = cache.get("INDORE", "LINE-01", "REACTOR_TEMP_C").orElseThrow();
        assertThat(cleared.severity()).isEqualTo(AlarmSeverity.NONE);
        assertThat(cleared.alarmFrozen()).isFalse();
        assertThat(cache.findAlarms("INDORE", "LINE-01")).isEmpty();
    }

    private void ingest(double value, String quality) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("siteId", "INDORE");
        body.put("lineId", "LINE-01");
        body.put("tagCode", "REACTOR_TEMP_C");
        body.put("value", value);
        body.put("quality", quality);
        body.put("sourceTimestamp", "2026-11-19T12:00:00Z");
        IngestResult result = ingestService.ingestHttp(body);
        assertThat(result.accepted()).isEqualTo(1);
        assertThat(result.bufferFull()).isFalse();
    }

    private static TagMapping tempTag() {
        return new TagMapping(
                UUID.randomUUID(),
                "INDORE",
                "LINE-01",
                "REACTOR_TEMP_C",
                "Reactor temperature",
                Protocol.OPC_UA,
                "opc.tcp://localhost",
                "ns=3;i=1002",
                TagDataType.FLOAT,
                "°C",
                false,
                null,
                null,
                0.2,
                30.0,
                70.0,
                20.0,
                80.0,
                null);
    }
}
