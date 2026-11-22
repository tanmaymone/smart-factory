package com.factory.central.ingestion;

import java.time.Instant;

public record TelemetryHistoryResponse(
        Instant time,
        String siteId,
        String lineId,
        String tagCode,
        Double numericValue,
        Boolean booleanValue,
        String stringValue,
        String quality) {

    public static TelemetryHistoryResponse from(TelemetryHistoryRow row) {
        return new TelemetryHistoryResponse(
                row.time(),
                row.siteId(),
                row.lineId(),
                row.tagCode(),
                row.numericValue(),
                row.booleanValue(),
                row.stringValue(),
                row.quality());
    }
}
