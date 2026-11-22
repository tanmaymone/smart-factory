package com.factory.central.cache;

import java.time.Instant;

public record LatestTagState(
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
        Instant alarmTimestamp) {

    public LatestTagState withSample(Object value, String quality, Instant timestamp) {
        return new LatestTagState(
                siteId,
                lineId,
                tagCode,
                value,
                quality,
                timestamp,
                severity,
                direction,
                alarmFrozen,
                alarmValue,
                alarmTimestamp);
    }

    public LatestTagState withAlarm(
            AlarmSeverity severity,
            AlarmDirection direction,
            boolean alarmFrozen,
            Object alarmValue,
            Instant alarmTimestamp) {
        return new LatestTagState(
                siteId,
                lineId,
                tagCode,
                value,
                quality,
                timestamp,
                severity,
                direction,
                alarmFrozen,
                alarmValue,
                alarmTimestamp);
    }

    public static LatestTagState initial(
            String siteId,
            String lineId,
            String tagCode,
            Object value,
            String quality,
            Instant timestamp,
            AlarmSeverity severity,
            AlarmDirection direction) {
        boolean alarming = severity != null && severity != AlarmSeverity.NONE;
        return new LatestTagState(
                siteId,
                lineId,
                tagCode,
                value,
                quality,
                timestamp,
                severity == null ? AlarmSeverity.NONE : severity,
                direction,
                false,
                alarming ? value : null,
                alarming ? timestamp : null);
    }
}
