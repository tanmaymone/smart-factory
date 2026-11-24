package com.factory.edge.buffer;

import com.factory.edge.opcua.TagReading;
import java.time.Instant;

/**
 * One filtered telemetry reading destined for the outbound queue / MQTT link (M2.3).
 */
public record TelemetryRecord(
        String siteId,
        String lineId,
        String tagCode,
        String nodeId,
        Object value,
        String valueType,
        String quality,
        String statusName,
        Instant sourceTimestamp,
        Instant serverTimestamp,
        Boolean nodeWritable) {

    public static final String TYPE_NULL = "NULL";
    public static final String TYPE_INT32 = "INT32";
    public static final String TYPE_INT64 = "INT64";
    public static final String TYPE_FLOAT = "FLOAT";
    public static final String TYPE_DOUBLE = "DOUBLE";
    public static final String TYPE_BOOLEAN = "BOOLEAN";
    public static final String TYPE_STRING = "STRING";
    public static final String TYPE_OTHER = "OTHER";

    public static TelemetryRecord from(TagReading reading) {
        return new TelemetryRecord(
                reading.siteId(),
                reading.lineId(),
                reading.tagCode(),
                reading.nodeId(),
                reading.value(),
                typeOf(reading.value()),
                reading.quality(),
                reading.statusName(),
                reading.sourceTimestamp(),
                reading.serverTimestamp(),
                reading.nodeWritable());
    }

    public static String typeOf(Object value) {
        if (value == null) {
            return TYPE_NULL;
        }
        if (value instanceof Integer) {
            return TYPE_INT32;
        }
        if (value instanceof Long) {
            return TYPE_INT64;
        }
        if (value instanceof Float) {
            return TYPE_FLOAT;
        }
        if (value instanceof Double) {
            return TYPE_DOUBLE;
        }
        if (value instanceof Boolean) {
            return TYPE_BOOLEAN;
        }
        if (value instanceof String) {
            return TYPE_STRING;
        }
        if (value instanceof Byte || value instanceof Short) {
            return TYPE_INT32;
        }
        return TYPE_OTHER;
    }
}
