package com.factory.central.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.factory.central.cache.AlarmDirection;
import com.factory.central.cache.AlarmSeverity;
import com.factory.central.cache.InMemoryLatestTagCache;
import com.factory.central.cache.LatestTagState;
import com.factory.central.registry.Protocol;
import com.factory.central.registry.TagDataType;
import com.factory.central.registry.TagMapping;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("SF-M1.6")
class InletValveOpenInterlockRuleTest {

    private final InletValveOpenInterlockRule rule = new InletValveOpenInterlockRule();
    private InMemoryLatestTagCache cache;
    private TagMapping targetTag;

    @BeforeEach
    void setUp() {
        cache = new InMemoryLatestTagCache();
        targetTag = new TagMapping(
                UUID.randomUUID(),
                "INDORE",
                "LINE-01",
                "REACTOR_TEMP_SP",
                "setpoint",
                Protocol.OPC_UA,
                "opc.tcp://localhost",
                "ns=3;i=1003",
                TagDataType.FLOAT,
                "°C",
                true,
                15.0,
                90.0,
                0.1,
                null,
                null,
                null,
                null,
                InletValveOpenInterlockRule.KEY);
    }

    @Test
    void acceptsFiniteGoodValuesAtThresholdAcrossNumericTypes() {
        for (Object value : new Object[] {Integer.valueOf(5), Long.valueOf(5L), Double.valueOf(5.0), new BigDecimal("5")}) {
            cache.put(sample(value, "GOOD"));
            assertThat(rule.violation(ctx())).isEmpty();
        }
    }

    @Test
    void rejectsMissingSample() {
        assertThat(rule.violation(ctx()).orElseThrow()).contains("VALVE_101_POS sample is missing");
    }

    @Test
    void rejectsNullTimestamp() {
        cache.put(new LatestTagState(
                "INDORE",
                "LINE-01",
                "VALVE_101_POS",
                10.0,
                "GOOD",
                null,
                AlarmSeverity.NONE,
                null,
                false,
                null,
                null));
        assertThat(rule.violation(ctx()).orElseThrow()).contains("timestamp is missing");
    }

    @Test
    void rejectsNonGoodQualityEvenWhenNumericAboveThreshold() {
        cache.put(sample(80.0, "BAD"));
        assertThat(rule.violation(ctx()).orElseThrow()).contains("quality is not GOOD");

        cache.put(sample(80.0, "UNCERTAIN"));
        assertThat(rule.violation(ctx()).orElseThrow()).contains("quality is not GOOD");

        cache.put(sample(80.0, ""));
        assertThat(rule.violation(ctx()).orElseThrow()).contains("quality is not GOOD");
    }

    @Test
    void rejectsNonNumericValuesWithoutClassCast() {
        cache.put(sample(Boolean.TRUE, "GOOD"));
        assertThat(rule.violation(ctx()).orElseThrow()).contains("value is not numeric");

        cache.put(sample("open", "GOOD"));
        assertThat(rule.violation(ctx()).orElseThrow()).contains("value is not numeric");
    }

    @Test
    void rejectsBelowThreshold() {
        cache.put(sample(4.9, "GOOD"));
        assertThat(rule.violation(ctx()).orElseThrow()).contains("must be >= 5.0");
    }

    private InterlockContext ctx() {
        return new InterlockContext(targetTag, 50.0, cache);
    }

    private static LatestTagState sample(Object value, String quality) {
        return LatestTagState.initial(
                "INDORE",
                "LINE-01",
                "VALVE_101_POS",
                value,
                quality,
                Instant.parse("2026-11-19T12:00:00Z"),
                AlarmSeverity.NONE,
                AlarmDirection.LOW);
    }
}
