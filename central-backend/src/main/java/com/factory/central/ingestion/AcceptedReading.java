package com.factory.central.ingestion;

import com.factory.central.registry.TagMapping;
import java.time.Instant;

record AcceptedReading(TagMapping tag, Object value, String quality, Instant timestamp, TelemetryHistoryRow historyRow) {}
