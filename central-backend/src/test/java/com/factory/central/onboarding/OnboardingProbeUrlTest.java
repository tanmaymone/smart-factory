package com.factory.central.onboarding;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Boundaries the fixture cannot carry: the accepted side of each rule. The rejected side is
 * contracts/onboarding-probe-validation.json, asserted through the controller.
 */
@Tag("EDGE-PROBE")
class OnboardingProbeUrlTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "opc.tcp://localhost:53530/OPCUA/SimulationServer",
                "OPC.TCP://localhost:53530/OPCUA/SimulationServer",
                "opc.tcp://localhost:53530",
                "opc.tcp://localhost/OPCUA/SimulationServer",
                "opc.tcp://plc_1/OPCUA/SimulationServer",
                "opc.tcp://PLC-1.plant.local:1/OPCUA",
                "opc.tcp://localhost:65535",
                "opc.tcp://[::1]:4840/OPCUA/SimulationServer",
                "opc.tcp://[::1]/OPCUA/SimulationServer",
                "opc.tcp://[::ffff:192.0.2.1]:4840/OPCUA/SimulationServer",
                "opc.tcp://localhost:53530/path%3Fkeep%23keep"
            })
    void acceptsDialableShapes(String endpointUrl) {
        assertThat(OnboardingProbeUrl.isValid(endpointUrl)).isTrue();
    }

    @Test
    void hostLengthCapIs253CharactersMeasuredWithoutBrackets() {
        assertThat(OnboardingProbeUrl.isValid("opc.tcp://" + "a".repeat(253) + ":4840"))
                .isTrue();
        assertThat(OnboardingProbeUrl.isValid("opc.tcp://" + "a".repeat(254) + ":4840"))
                .isFalse();
        // A bracket interior is also capped, though java.net.URI rejects an over-long IPv6 literal first.
        assertThat(OnboardingProbeUrl.isValid("opc.tcp://[" + "1".repeat(254) + "]:4840"))
                .isFalse();
        assertThat(OnboardingProbeUrl.isValid("opc.tcp://[hello]:4840")).isFalse();
    }

    @Test
    void bracketedIpv6RequiresAColonBeforeThePort() {
        assertThat(OnboardingProbeUrl.isValid("opc.tcp://[::1]4840")).isFalse();
        assertThat(OnboardingProbeUrl.isValid("opc.tcp://[::1]4840/OPCUA/SimulationServer")).isFalse();
        assertThat(OnboardingProbeUrl.isValid("opc.tcp://[::1]:4840")).isTrue();
        assertThat(OnboardingProbeUrl.isValid("opc.tcp://[::1]/OPCUA/SimulationServer")).isTrue();
    }

    @Test
    void portSegmentIsARegexCheckNotParseIntSuccess() {
        assertThat(OnboardingProbeUrl.isValid("opc.tcp://localhost:1")).isTrue();
        assertThat(OnboardingProbeUrl.isValid("opc.tcp://localhost:65535")).isTrue();
        assertThat(OnboardingProbeUrl.isValid("opc.tcp://localhost:65536")).isFalse();
        assertThat(OnboardingProbeUrl.isValid("opc.tcp://localhost:0080")).isFalse();
        assertThat(OnboardingProbeUrl.isValid("opc.tcp://localhost:+80")).isFalse();
        assertThat(OnboardingProbeUrl.isValid("opc.tcp://localhost:999999")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "opc.tcp://localhost:53530/OPCUA/SimulationServer?foo=bar",
                "opc.tcp://localhost:53530/OPCUA/SimulationServer#frag",
                "opc.tcp://localhost:53530/OPCUA/SimulationServer?",
                "opc.tcp://localhost:53530/OPCUA/SimulationServer#",
                "opc.tcp://localhost:53530?foo=bar",
                "opc.tcp://localhost:53530#frag",
                "opc.tcp://localhost:53530/OPCUA/SimulationServer#frag?still-fragment"
            })
    void queryAndFragmentAreRejected(String endpointUrl) {
        assertThat(OnboardingProbeUrl.isValid(endpointUrl)).isFalse();
    }

    @Test
    void rejectsSecondColonOutsideBracketsAndWrongScheme() {
        assertThat(OnboardingProbeUrl.isValid("opc.tcp://localhost:1:2")).isFalse();
        assertThat(OnboardingProbeUrl.isValid("tcp://localhost:4840")).isFalse();
        assertThat(OnboardingProbeUrl.isValid("http://localhost:4840")).isFalse();
        assertThat(OnboardingProbeUrl.isValid(null)).isFalse();
    }
}
