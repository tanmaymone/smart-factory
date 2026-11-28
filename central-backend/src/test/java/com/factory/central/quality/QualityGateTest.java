package com.factory.central.quality;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("EDGE-QUALITY")
class QualityGateTest {

    @Test
    void acceptsOnlyTrimmedUpperGood() {
        assertThat(QualityGate.requireGood("GOOD")).isTrue();
        assertThat(QualityGate.requireGood(" good ")).isTrue();
        assertThat(QualityGate.requireGood(null)).isFalse();
        assertThat(QualityGate.requireGood("")).isFalse();
        assertThat(QualityGate.requireGood("UNCERTAIN")).isFalse();
        assertThat(QualityGate.requireGood("BAD")).isFalse();
    }
}