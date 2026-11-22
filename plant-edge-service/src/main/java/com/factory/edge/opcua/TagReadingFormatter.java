package com.factory.edge.opcua;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Stable single-line log format for M2.1 probe output and M2.3 telemetry mapping.
 */
public final class TagReadingFormatter {

    private static final DateTimeFormatter ISO_MS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private TagReadingFormatter() {}

    public static String format(TagReading reading) {
        StringBuilder sb = new StringBuilder(192);
        sb.append("OPC event=").append(reading.event());
        sb.append(" siteId=").append(nullToLiteral(reading.siteId()));
        sb.append(" lineId=").append(nullToLiteral(reading.lineId()));
        sb.append(" tagCode=").append(nullToLiteral(reading.tagCode()));
        sb.append(" nodeId=").append(nullToLiteral(reading.nodeId()));
        sb.append(" value=").append(nullToLiteral(reading.value()));
        sb.append(" quality=").append(nullToLiteral(reading.quality()));
        sb.append(" sourceTimestamp=").append(formatInstant(reading.sourceTimestamp()));
        sb.append(" serverTimestamp=").append(formatInstant(reading.serverTimestamp()));
        if (reading.statusName() != null && !reading.statusName().isBlank()) {
            sb.append(" status=").append(reading.statusName());
        }
        return sb.toString();
    }

    private static String formatInstant(Instant instant) {
        if (instant == null) {
            return "null";
        }
        return ISO_MS.format(instant);
    }

    private static String nullToLiteral(Object value) {
        return value == null ? "null" : String.valueOf(value);
    }
}
