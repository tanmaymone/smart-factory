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
        Instant alarmTimestamp,
        String observedDataType,
        Boolean nodeWritable) {

    public LatestTagState withSample(
            Object value,
            String quality,
            Instant timestamp,
            String observedDataType,
            Boolean nodeWritable) {
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
                alarmTimestamp,
                observedDataType != null ? observedDataType : this.observedDataType,
                nodeWritable != null ? nodeWritable : this.nodeWritable);
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
                alarmTimestamp,
                observedDataType,
                nodeWritable);
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
        return initial(siteId, lineId, tagCode, value, quality, timestamp, severity, direction, null, null);
    }

    public static LatestTagState initial(
            String siteId,
            String lineId,
            String tagCode,
            Object value,
            String quality,
            Instant timestamp,
            AlarmSeverity severity,
            AlarmDirection direction,
            String observedDataType,
            Boolean nodeWritable) {
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
                alarming ? timestamp : null,
                observedDataType,
                nodeWritable);
    }
}
