package com.factory.edge.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.factory.edge.opcua.OpcUaProperties;
import com.factory.edge.opcua.TagReading;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("EDGE-DEADBAND")
class DeadbandFilterTest {

    private DeadbandFilter filter;

    @BeforeEach
    void setUp() {
        filter = new DeadbandFilter(List.of(tag("INDORE", "LINE-01", "VALVE_101_POS", 0.5)));
    }

    @Test
    void firstObservationAlwaysEmits() {
        assertThat(filter.shouldEmit(good(50.0))).isTrue();
    }

    @Test
    void holdsInsideDeadband() {
        assertThat(filter.shouldEmit(good(50.0))).isTrue();
        assertThat(filter.shouldEmit(good(50.4))).isFalse();
        assertThat(filter.shouldEmit(good(49.6))).isFalse();
    }

    @Test
    void emitsAtExactDeadband() {
        assertThat(filter.shouldEmit(good(50.0))).isTrue();
        assertThat(filter.shouldEmit(good(50.5))).isTrue();
        assertThat(filter.shouldEmit(good(50.9))).isFalse();
        assertThat(filter.shouldEmit(good(51.0))).isTrue();
    }

    @Test
    void booleanFlipEmitsRepeatHolds() {
        DeadbandFilter boolFilter =
                new DeadbandFilter(List.of(tag("INDORE", "LINE-01", "PUMP_ON", 0.0)));
        assertThat(boolFilter.shouldEmit(goodBool(true))).isTrue();
        assertThat(boolFilter.shouldEmit(goodBool(true))).isFalse();
        assertThat(boolFilter.shouldEmit(goodBool(false))).isTrue();
    }

    @Test
    void integerVsDoubleAlwaysEmits() {
        assertThat(filter.shouldEmit(good(50))).isTrue();
        assertThat(filter.shouldEmit(good(50.0))).isTrue();
    }

    @Test
    void integralOneAndLongOneHoldWithZeroBand() {
        DeadbandFilter zero = new DeadbandFilter(List.of(tag("INDORE", "LINE-01", "COUNT", 0.0)));
        assertThat(zero.shouldEmit(good(1))).isTrue();
        assertThat(zero.shouldEmit(good(1L))).isFalse();
    }

    @Test
    void positiveZeroAndNegativeZeroHold() {
        DeadbandFilter zero = new DeadbandFilter(List.of(tag("INDORE", "LINE-01", "VALVE_101_POS", 0.0)));
        assertThat(zero.shouldEmit(good(0.0))).isTrue();
        assertThat(zero.shouldEmit(good(-0.0))).isFalse();
    }

    @Test
    void nanRepeatHoldsThenFiniteEmits() {
        assertThat(filter.shouldEmit(good(Double.NaN))).isTrue();
        assertThat(filter.shouldEmit(good(Double.NaN))).isFalse();
        assertThat(filter.shouldEmit(good(10.0))).isTrue();
        assertThat(filter.shouldEmit(good(10.1))).isFalse();
    }

    @Test
    void infinityKindChangeEmits() {
        assertThat(filter.shouldEmit(good(Double.POSITIVE_INFINITY))).isTrue();
        assertThat(filter.shouldEmit(good(Double.POSITIVE_INFINITY))).isFalse();
        assertThat(filter.shouldEmit(good(Double.NEGATIVE_INFINITY))).isTrue();
    }

    @Test
    void badTransitionEmitsThenRepeatHolds() {
        assertThat(filter.shouldEmit(good(50.0))).isTrue();
        assertThat(filter.shouldEmit(bad(null, "Bad_Timeout"))).isTrue();
        assertThat(filter.shouldEmit(bad(null, "Bad_Timeout"))).isFalse();
        assertThat(filter.shouldEmit(bad(1.0, "Bad_OutOfService"))).isTrue();
    }

    @Test
    void goodReturnInsideOldBaselineHolds() {
        assertThat(filter.shouldEmit(good(50.0))).isTrue();
        assertThat(filter.shouldEmit(bad(null, "Bad_Timeout"))).isTrue();
        assertThat(filter.shouldEmit(good(50.2))).isFalse();
        assertThat(filter.shouldEmit(good(50.5))).isTrue();
    }

    @Test
    void uncertainDriftHoldAndCross() {
        assertThat(filter.shouldEmit(uncertain(50.0, "Uncertain_LastUsableValue"))).isTrue();
        assertThat(filter.shouldEmit(uncertain(50.4, "Uncertain_LastUsableValue"))).isFalse();
        assertThat(filter.shouldEmit(uncertain(50.5, "Uncertain_LastUsableValue"))).isTrue();
        assertThat(filter.shouldEmit(good(50.2))).isTrue();
        assertThat(filter.shouldEmit(good(50.4))).isFalse();
    }

    @Test
    void perSiteIsolation() {
        DeadbandFilter multi =
                new DeadbandFilter(
                        List.of(
                                tag("INDORE", "LINE-01", "VALVE_101_POS", 0.5),
                                tag("PUNE", "LINE-01", "VALVE_101_POS", 0.5)));
        assertThat(multi.shouldEmit(goodAt("INDORE", 50.0))).isTrue();
        assertThat(multi.shouldEmit(goodAt("PUNE", 50.0))).isTrue();
        assertThat(multi.shouldEmit(goodAt("INDORE", 50.2))).isFalse();
        assertThat(multi.shouldEmit(goodAt("PUNE", 50.6))).isTrue();
    }

    @Test
    void negativeDeadbandThrows() {
        assertThatThrownBy(
                        () ->
                                new DeadbandFilter(
                                        List.of(tag("INDORE", "LINE-01", "VALVE_101_POS", -0.1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deadband must be >= 0");
    }

    private static OpcUaProperties.TagConfig tag(
            String siteId, String lineId, String tagCode, double deadband) {
        OpcUaProperties.TagConfig config = new OpcUaProperties.TagConfig();
        config.setSiteId(siteId);
        config.setLineId(lineId);
        config.setTagCode(tagCode);
        config.setNodeId("ns=3;i=1001");
        config.setDeadband(deadband);
        return config;
    }

    private static TagReading good(Object value) {
        return reading("GOOD", value, null, "INDORE");
    }

    private static TagReading goodAt(String siteId, Object value) {
        return reading("GOOD", value, null, siteId);
    }

    private static TagReading goodBool(boolean value) {
        return new TagReading(
                TagReading.EVENT_CHANGE,
                "INDORE",
                "LINE-01",
                "PUMP_ON",
                "ns=3;i=2001",
                value,
                "GOOD",
                Instant.parse("2026-11-20T12:00:00Z"),
                Instant.parse("2026-11-20T12:00:00Z"),
                null);
    }

    private static TagReading bad(Object value, String status) {
        return reading("BAD", value, status, "INDORE");
    }

    private static TagReading uncertain(Object value, String status) {
        return reading("UNCERTAIN", value, status, "INDORE");
    }

    private static TagReading reading(String quality, Object value, String status, String siteId) {
        return new TagReading(
                TagReading.EVENT_CHANGE,
                siteId,
                "LINE-01",
                "VALVE_101_POS",
                "ns=3;i=1001",
                value,
                quality,
                Instant.parse("2026-11-20T12:00:00Z"),
                Instant.parse("2026-11-20T12:00:00Z"),
                status);
    }
}
