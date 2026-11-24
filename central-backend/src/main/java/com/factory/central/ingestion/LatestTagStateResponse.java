package com.factory.central.ingestion;

import com.factory.central.cache.AlarmDirection;
import com.factory.central.cache.AlarmSeverity;
import com.factory.central.cache.LatestTagState;
import java.time.Instant;

public record LatestTagStateResponse(
        String siteId,
        String lineId,
        String tagCode,
        Object value,
        String quality,
        Instant timestamp,
        AlarmSeverity severity,
        AlarmDirection direction,
        boolean alarmFrozen,
        Object alarmValue,
        Instant alarmTimestamp,
        String observedDataType,
        Boolean nodeWritable) {

    public static LatestTagStateResponse from(LatestTagState state) {
        return new LatestTagStateResponse(
                state.siteId(),
                state.lineId(),
                state.tagCode(),
                state.value(),
                state.quality(),
                state.timestamp(),
                state.severity(),
                state.direction(),
                state.alarmFrozen(),
                state.alarmValue(),
                state.alarmTimestamp(),
                state.observedDataType(),
                state.nodeWritable());
    }
}
