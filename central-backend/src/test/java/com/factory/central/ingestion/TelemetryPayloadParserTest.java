package com.factory.central.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.factory.central.ingestion.TelemetryPayloadParser.ParsedPayload;
import com.factory.central.ingestion.TelemetryPayloadParser.PayloadParseException;
import java.time.Instant;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("SF-M1.4")
class TelemetryPayloadParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesSection71SampleEnvelope() throws Exception {
        String json =
                """
                {
                  "messageId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
                  "siteId": "INDORE",
                  "lineId": "LINE-01",
                  "timestamp": "2026-11-19T12:00:01.250Z",
                  "readings": [
                    {
                      "tagCode": "VALVE_101_POS",
                      "value": 75.5,
                      "dataType": "FLOAT",
                      "quality": "GOOD",
                      "sourceTimestamp": "2026-11-19T12:00:01.200Z"
                    }
                  ]
                }
                """;
        ParsedPayload payload = TelemetryPayloadParser.parse(objectMapper.readTree(json));
        assertThat(payload.siteId()).isEqualTo("INDORE");
        assertThat(payload.lineId()).isEqualTo("LINE-01");
        assertThat(payload.readings()).hasSize(1);
        assertThat(payload.readings().getFirst().tagCode()).isEqualTo("VALVE_101_POS");
        assertThat(payload.envelopeTimestamp()).isEqualTo(Instant.parse("2026-11-19T12:00:01.250Z"));
    }

    @Test
    void parsesFlatSingleReading() throws Exception {
        String json =
                """
                {
                  "siteId": "INDORE",
                  "lineId": "LINE-01",
                  "tagCode": "VALVE_101_POS",
                  "value": 10,
                  "quality": "GOOD",
                  "sourceTimestamp": "2026-11-19T12:00:01.200Z"
                }
                """;
        ParsedPayload payload = TelemetryPayloadParser.parse(objectMapper.readTree(json));
        assertThat(payload.readings()).hasSize(1);
        assertThat(payload.readings().getFirst().tagCode()).isEqualTo("VALVE_101_POS");
    }

    @Test
    void parsesAddressOnlyReading() throws Exception {
        String json =
                """
                {
                  "siteId": "INDORE",
                  "lineId": "LINE-01",
                  "timestamp": "2026-11-19T12:00:01.250Z",
                  "readings": [
                    { "externalAddress": "nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=1002", "value": 55.0, "quality": "GOOD" }
                  ]
                }
                """;
        ParsedPayload payload = TelemetryPayloadParser.parse(objectMapper.readTree(json));
        assertThat(payload.readings().getFirst().externalAddress()).isEqualTo("nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=1002");
        assertThat(payload.readings().getFirst().tagCode()).isNull();
    }

    @Test
    void normalizesOffsetTimestampToUtcInstant() throws Exception {
        String json =
                """
                {
                  "siteId": "INDORE",
                  "lineId": "LINE-01",
                  "timestamp": "2026-11-19T17:30:00+05:30",
                  "readings": []
                }
                """;
        ParsedPayload payload = TelemetryPayloadParser.parse(objectMapper.readTree(json));
        assertThat(payload.envelopeTimestamp()).isEqualTo(Instant.parse("2026-11-19T12:00:00Z"));
    }

    @Test
    void rejectsZoneLessTimestamp() {
        String json =
                """
                {
                  "siteId": "INDORE",
                  "lineId": "LINE-01",
                  "timestamp": "2026-11-19T12:00:00",
                  "readings": []
                }
                """;
        assertThatThrownBy(() -> TelemetryPayloadParser.parse(objectMapper.readTree(json)))
                .isInstanceOf(PayloadParseException.class)
                .extracting(ex -> ((PayloadParseException) ex).getCode())
                .isEqualTo(IngestionErrorCode.INVALID_VALUE);
    }

    @Test
    void readsNodeWritableAndNormalizesObservedType() throws Exception {
        String json =
                """
                {
                  "siteId": "INDORE",
                  "lineId": "LINE-01",
                  "readings": [
                    { "tagCode": "VALVE_101_POS", "value": 12, "dataType": "int", "nodeWritable": false }
                  ]
                }
                """;
        var reading = TelemetryPayloadParser.parse(objectMapper.readTree(json)).readings().getFirst();
        assertThat(reading.nodeWritable()).isFalse();
        assertThat(TelemetryPayloadParser.observedDataType(reading.dataType())).isEqualTo("INT");
        assertThat(TelemetryPayloadParser.observedDataType("custom")).isNull();
    }
}
