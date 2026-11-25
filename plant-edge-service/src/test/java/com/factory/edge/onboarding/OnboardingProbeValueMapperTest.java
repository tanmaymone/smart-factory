package com.factory.edge.onboarding;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.time.Instant;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.Matrix;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.ULong;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("EDGE-PROBE")
class OnboardingProbeValueMapperTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void nullStaysNull() {
        assertThat(OnboardingProbeValueMapper.toJsonValue(null)).isNull();
    }

    @Test
    void booleanStaysAJsonBoolean() throws Exception {
        Object mapped = OnboardingProbeValueMapper.toJsonValue(Boolean.TRUE);

        assertThat(mapped).isEqualTo(Boolean.TRUE);
        assertThat(mapper.writeValueAsString(mapped)).isEqualTo("true");
    }

    @Test
    void uLongMaxBecomesThatExactJsonNumber() throws Exception {
        Object mapped = OnboardingProbeValueMapper.toJsonValue(ULong.MAX);

        assertThat(mapped).isEqualTo(new BigInteger("18446744073709551615"));
        assertThat(mapper.writeValueAsString(mapped)).isEqualTo("18446744073709551615");
    }

    @Test
    void unsignedScalarsBecomeJsonNumbers() throws Exception {
        assertThat(mapper.writeValueAsString(OnboardingProbeValueMapper.toJsonValue(Unsigned.ubyte(7))))
                .isEqualTo("7");
        assertThat(mapper.writeValueAsString(OnboardingProbeValueMapper.toJsonValue(Unsigned.ushort(7))))
                .isEqualTo("7");
        assertThat(mapper.writeValueAsString(OnboardingProbeValueMapper.toJsonValue(Unsigned.uint(7))))
                .isEqualTo("7");
    }

    @Test
    void nonFiniteScalarsBecomeNullAndFiniteOnesStayNumbers() {
        assertThat(OnboardingProbeValueMapper.toJsonValue(Double.NaN)).isNull();
        assertThat(OnboardingProbeValueMapper.toJsonValue(Double.POSITIVE_INFINITY)).isNull();
        assertThat(OnboardingProbeValueMapper.toJsonValue(Double.NEGATIVE_INFINITY)).isNull();
        assertThat(OnboardingProbeValueMapper.toJsonValue(Float.NaN)).isNull();
        assertThat(OnboardingProbeValueMapper.toJsonValue(Float.NEGATIVE_INFINITY)).isNull();
        assertThat(OnboardingProbeValueMapper.toJsonValue(1.5d)).isEqualTo(1.5d);
        assertThat(OnboardingProbeValueMapper.toJsonValue(42)).isEqualTo(42);
    }

    @Test
    void dateTimeBecomesAnIsoString() {
        Object mapped =
                OnboardingProbeValueMapper.toJsonValue(
                        new DateTime(Instant.parse("2026-01-15T10:00:00Z")));

        assertThat(mapped).isEqualTo("2026-01-15T10:00:00Z");
    }

    @Test
    void nonScalarsBecomeJsonStringsNeverMiloObjects() throws Exception {
        assertThat(OnboardingProbeValueMapper.toJsonValue(LocalizedText.english("hello")))
                .isInstanceOf(String.class);
        assertThat(OnboardingProbeValueMapper.toJsonValue(ByteString.of(new byte[] {1, 2})))
                .isInstanceOf(String.class);
        assertThat(OnboardingProbeValueMapper.toJsonValue(new NodeId(2, 1001)))
                .isInstanceOf(String.class);

        Object array = OnboardingProbeValueMapper.toJsonValue(new double[] {1.0, Double.NaN});
        Object matrix = OnboardingProbeValueMapper.toJsonValue(Matrix.ofInt32(new int[][] {{1, 2}}));

        assertThat(array).isInstanceOf(String.class);
        assertThat(matrix).isInstanceOf(String.class);
        // A JSON string is legal even when its characters contain NaN.
        assertThat(mapper.writeValueAsString(array)).startsWith("\"");
        assertThat(mapper.writeValueAsString(matrix)).startsWith("\"");
    }
}
