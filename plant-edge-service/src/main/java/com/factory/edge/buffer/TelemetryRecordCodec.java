package com.factory.edge.buffer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;

final class TelemetryRecordCodec {

    private final ObjectMapper mapper;

    TelemetryRecordCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    String encode(TelemetryRecord record) {
        try {
            ObjectNode node = mapper.createObjectNode();
            node.put("siteId", record.siteId());
            node.put("lineId", record.lineId());
            node.put("tagCode", record.tagCode());
            node.put("nodeId", record.nodeId());
            node.put("valueType", record.valueType());
            node.put("quality", record.quality());
            if (record.statusName() == null) {
                node.putNull("statusName");
            } else {
                node.put("statusName", record.statusName());
            }
            writeInstant(node, "sourceTimestamp", record.sourceTimestamp());
            writeInstant(node, "serverTimestamp", record.serverTimestamp());
            writeValue(node, record.valueType(), record.value());
            return mapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encode TelemetryRecord", e);
        }
    }

    TelemetryRecord decode(String json) {
        try {
            JsonNode node = mapper.readTree(json);
            String valueType = text(node, "valueType");
            return new TelemetryRecord(
                    text(node, "siteId"),
                    text(node, "lineId"),
                    text(node, "tagCode"),
                    text(node, "nodeId"),
                    readValue(node.get("value"), valueType),
                    valueType,
                    text(node, "quality"),
                    nullText(node, "statusName"),
                    readInstant(node, "sourceTimestamp"),
                    readInstant(node, "serverTimestamp"));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decode TelemetryRecord", e);
        }
    }

    private void writeValue(ObjectNode node, String valueType, Object value) {
        if (value == null || TelemetryRecord.TYPE_NULL.equals(valueType)) {
            node.putNull("value");
            return;
        }
        switch (valueType) {
            case TelemetryRecord.TYPE_INT32 -> node.put("value", ((Number) value).intValue());
            case TelemetryRecord.TYPE_INT64 -> node.put("value", ((Number) value).longValue());
            case TelemetryRecord.TYPE_FLOAT -> node.put("value", ((Number) value).floatValue());
            case TelemetryRecord.TYPE_DOUBLE -> node.put("value", ((Number) value).doubleValue());
            case TelemetryRecord.TYPE_BOOLEAN -> node.put("value", (Boolean) value);
            case TelemetryRecord.TYPE_STRING -> node.put("value", (String) value);
            default -> node.put("value", String.valueOf(value));
        }
    }

    private Object readValue(JsonNode valueNode, String valueType) {
        if (valueNode == null || valueNode.isNull() || TelemetryRecord.TYPE_NULL.equals(valueType)) {
            return null;
        }
        return switch (valueType) {
            case TelemetryRecord.TYPE_INT32 -> valueNode.intValue();
            case TelemetryRecord.TYPE_INT64 -> valueNode.longValue();
            case TelemetryRecord.TYPE_FLOAT -> (float) valueNode.doubleValue();
            case TelemetryRecord.TYPE_DOUBLE -> valueNode.doubleValue();
            case TelemetryRecord.TYPE_BOOLEAN -> valueNode.booleanValue();
            case TelemetryRecord.TYPE_STRING -> valueNode.asText();
            default -> valueNode.asText();
        };
    }

    private static void writeInstant(ObjectNode node, String field, Instant instant) {
        if (instant == null) {
            node.putNull(field);
        } else {
            node.put(field, instant.toString());
        }
    }

    private static Instant readInstant(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return Instant.parse(value.asText());
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String nullText(JsonNode node, String field) {
        return text(node, field);
    }
}
