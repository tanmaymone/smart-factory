package com.factory.central.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.factory.central.cache.AlarmDirection;
import com.factory.central.cache.AlarmSeverity;
import com.factory.central.cache.LatestTagState;
import java.time.Instant;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("SF-M1.5")
class AlarmTransitionPublishTest {

    @Test
    void firstNoneDoesNotTransition_firstCriticalDoes_repeatDoesNot_clearDoes() {
        LatestTagState none = state(AlarmSeverity.NONE, null, false);
        LatestTagState critical = state(AlarmSeverity.CRITICAL, AlarmDirection.HIGH, false);
        LatestTagState criticalAgain = state(AlarmSeverity.CRITICAL, AlarmDirection.HIGH, false);
        LatestTagState frozen = state(AlarmSeverity.CRITICAL, AlarmDirection.HIGH, true);
        LatestTagState cleared = state(AlarmSeverity.NONE, null, false);

        assertThat(TelemetryIngestService.alarmTransitioned(null, none)).isFalse();
        assertThat(TelemetryIngestService.alarmTransitioned(null, critical)).isTrue();
        assertThat(TelemetryIngestService.alarmTransitioned(critical, criticalAgain)).isFalse();
        assertThat(TelemetryIngestService.alarmTransitioned(critical, frozen)).isTrue();
        assertThat(TelemetryIngestService.alarmTransitioned(frozen, cleared)).isTrue();
    }

    private static LatestTagState state(AlarmSeverity severity, AlarmDirection direction, boolean frozen) {
        LatestTagState base = LatestTagState.initial(
                "INDORE",
                "LINE-01",
                "REACTOR_TEMP_C",
                80.0,
                "GOOD",
                Instant.parse("2026-11-19T12:00:00Z"),
                severity,
                direction);
        if (!frozen) {
            return base;
        }
        return base.withAlarm(severity, direction, true, 80.0, Instant.parse("2026-11-19T12:00:00Z"));
    }
}
