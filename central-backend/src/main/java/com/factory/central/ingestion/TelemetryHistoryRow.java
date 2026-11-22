package com.factory.central.ingestion;

import java.time.Instant;

public record TelemetryHistoryRow(
        Instant time,
        String siteId,
        String lineId,
        String tagCode,
        Double numericValue,
        Boolean booleanValue,
        String stringValue,
        String quality) {}
