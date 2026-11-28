package com.factory.central.stream;

import com.factory.central.cache.AlarmDirection;
import com.factory.central.cache.AlarmSeverity;
import com.factory.central.cache.LatestTagState;
import java.time.Instant;

/** SSE {@code TAG_UPDATE} / {@code ALARM_EVENT} JSON shape (mirrors REST latest-tag fields). */
record StreamTagStatePayload(
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

    static StreamTagStatePayload from(LatestTagState state) {
        return new StreamTagStatePayload(
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