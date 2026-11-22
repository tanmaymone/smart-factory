package com.factory.edge.mqtt;

import static org.assertj.core.api.Assertions.assertThat;

import com.factory.edge.buffer.TelemetryRecord;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("EDGE-MQTT")
class TelemetryEnvelopeMapperTest {

    private final TelemetryEnvelopeMapper mapper = new TelemetryEnvelopeMapper(new ObjectMapper());
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void buildsSection71EnvelopeWithNodeIdAndUtcTimestamp() throws Exception {
        TelemetryRecord record =
                new TelemetryRecord(
                        "INDORE",
                        "LINE-01",
                        "VALVE_101_POS",
                        "ns=3;i=1001",
                        75.5d,
                        TelemetryRecord.TYPE_DOUBLE,
                        "GOOD",
                        null,
                        Instant.parse("2026-11-19T12:00:01.200Z"),
                        Instant.parse("2026-11-19T12:00:01.250Z"));

        JsonNode root = json.readTree(mapper.toJson(record));
        assertThat(root.get("siteId").asText()).isEqualTo("INDORE");
        assertThat(root.get("lineId").asText()).isEqualTo("LINE-01");
        assertThat(root.get("timestamp").asText()).endsWith("Z");
        assertThat(root.get("messageId").asText()).isNotBlank();

        JsonNode reading = root.get("readings").get(0);
        assertThat(reading.get("tagCode").asText()).isEqualTo("VALVE_101_POS");
        assertThat(reading.get("nodeId").asText()).isEqualTo("ns=3;i=1001");
        assertThat(reading.get("value").asDouble()).isEqualTo(75.5d);
        assertThat(reading.get("dataType").asText()).isEqualTo("FLOAT");
        assertThat(reading.get("quality").asText()).isEqualTo("GOOD");
        assertThat(reading.get("sourceTimestamp").asText()).isEqualTo("2026-11-19T12:00:01.200Z");

        assertThat(TelemetryEnvelopeMapper.topic(record))
                .isEqualTo("site/INDORE/line/LINE-01/telemetry");
    }

    @Test
    void fallsBackToServerTimestampWhenSourceMissing() throws Exception {
        TelemetryRecord record =
                new TelemetryRecord(
                        "INDORE",
                        "LINE-01",
                        "VALVE_101_POS",
                        "ns=3;i=1001",
                        10.0d,
                        TelemetryRecord.TYPE_DOUBLE,
                        "GOOD",
                        null,
                        null,
                        Instant.parse("2026-11-19T12:00:02Z"));
        JsonNode root = json.readTree(mapper.toJson(record));
        assertThat(root.get("timestamp").asText()).isEqualTo("2026-11-19T12:00:02Z");
        assertThat(root.get("readings").get(0).has("sourceTimestamp")).isFalse();
    }
}
