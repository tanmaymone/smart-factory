package com.factory.edge.mqtt;

import com.factory.edge.buffer.TelemetryRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.UUID;

/** Maps a filtered {@link TelemetryRecord} to the Section 7.1 MQTT telemetry envelope. */
final class TelemetryEnvelopeMapper {

    private final ObjectMapper mapper;

    TelemetryEnvelopeMapper(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    String toJson(TelemetryRecord record) {
        try {
            Instant stamp = resolveTimestamp(record);
            ObjectNode root = mapper.createObjectNode();
            root.put("messageId", UUID.randomUUID().toString());
            root.put("siteId", record.siteId());
            root.put("lineId", record.lineId());
            root.put("timestamp", stamp.toString());

            ArrayNode readings = root.putArray("readings");
            ObjectNode reading = readings.addObject();
            reading.put("tagCode", record.tagCode());
            reading.put("nodeId", record.nodeId());
            writeValue(reading, record);
            reading.put("dataType", toWireDataType(record.valueType()));
            if (record.nodeWritable() != null) {
                reading.put("nodeWritable", record.nodeWritable());
            }
            reading.put(
                    "quality",
                    record.quality() == null || record.quality().isBlank() ? "" : record.quality());
            if (record.sourceTimestamp() != null) {
                reading.put("sourceTimestamp", record.sourceTimestamp().toString());
            }

            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encode MQTT telemetry envelope", e);
        }
    }

    static String topic(TelemetryRecord record) {
        return "site/" + record.siteId() + "/line/" + record.lineId() + "/telemetry";
    }

    static Instant resolveTimestamp(TelemetryRecord record) {
        if (record.sourceTimestamp() != null) {
            return record.sourceTimestamp();
        }
        if (record.serverTimestamp() != null) {
            return record.serverTimestamp();
        }
        return Instant.now();
    }

    static String toWireDataType(String valueType) {
        if (valueType == null) {
            return "STRING";
        }
        return switch (valueType) {
            case TelemetryRecord.TYPE_FLOAT, TelemetryRecord.TYPE_DOUBLE -> "FLOAT";
            case TelemetryRecord.TYPE_INT32, TelemetryRecord.TYPE_INT64 -> "INT";
            case TelemetryRecord.TYPE_BOOLEAN -> "BOOLEAN";
            case TelemetryRecord.TYPE_STRING -> "STRING";
            default -> "STRING";
        };
    }

    private static void writeValue(ObjectNode reading, TelemetryRecord record) {
        Object value = record.value();
        if (value == null) {
            reading.putNull("value");
            return;
        }
        switch (record.valueType()) {
            case TelemetryRecord.TYPE_INT32 -> reading.put("value", ((Number) value).intValue());
            case TelemetryRecord.TYPE_INT64 -> reading.put("value", ((Number) value).longValue());
            case TelemetryRecord.TYPE_FLOAT -> reading.put("value", ((Number) value).floatValue());
            case TelemetryRecord.TYPE_DOUBLE -> reading.put("value", ((Number) value).doubleValue());
            case TelemetryRecord.TYPE_BOOLEAN -> reading.put("value", (Boolean) value);
            case TelemetryRecord.TYPE_STRING -> reading.put("value", (String) value);
            default -> reading.put("value", String.valueOf(value));
        }
    }
}
