package com.factory.edge.opcua;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("EDGE-QUALITY")
class QualityGateTest {

    @Test
    void stringGateAcceptsOnlyTrimmedUpperGood() {
        assertThat(QualityGate.requireGood("GOOD")).isTrue();
        assertThat(QualityGate.requireGood(" good ")).isTrue();
        assertThat(QualityGate.requireGood("Good")).isTrue();
        assertThat(QualityGate.requireGood((String) null)).isFalse();
        assertThat(QualityGate.requireGood("")).isFalse();
        assertThat(QualityGate.requireGood("   ")).isFalse();
        assertThat(QualityGate.requireGood("BAD")).isFalse();
        assertThat(QualityGate.requireGood("UNCERTAIN")).isFalse();
    }

    @Test
    void dataValueGateRejectsUncertainAndNull() {
        assertThat(QualityGate.requireGood(new DataValue(new Variant(1), StatusCode.GOOD, null)))
                .isTrue();
        assertThat(QualityGate.requireGood(new DataValue(new Variant(1), StatusCode.UNCERTAIN, null)))
                .isFalse();
        assertThat(QualityGate.requireGood(new DataValue(new Variant(1), StatusCode.BAD, null)))
                .isFalse();
        assertThat(QualityGate.requireGood((DataValue) null)).isFalse();
        assertThat(QualityGate.requireGood((StatusCode) null)).isFalse();
        assertThat(QualityGate.requireGood(StatusCode.UNCERTAIN)).isFalse();
    }
}
