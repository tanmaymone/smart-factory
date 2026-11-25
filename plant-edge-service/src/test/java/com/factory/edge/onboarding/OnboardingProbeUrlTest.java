package com.factory.edge.onboarding;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("EDGE-PROBE")
class OnboardingProbeUrlTest {

    @Test
    void underscoreHostIsAcceptedAndKeepsPathWithDefaultPort() {
        OnboardingProbeUrl url =
                OnboardingProbeUrl.parse("opc.tcp://plc_1/OPCUA/SimulationServer").orElseThrow();

        assertThat(url.host()).isEqualTo("plc_1");
        assertThat(url.port()).isEqualTo(4840);
        assertThat(url.dialUrl()).isEqualTo("opc.tcp://plc_1:4840/OPCUA/SimulationServer");
    }

    @Test
    void uppercaseSchemeIsAcceptedAndDialsLowercase() {
        OnboardingProbeUrl url =
                OnboardingProbeUrl.parse("OPC.TCP://localhost:53530/OPCUA/SimulationServer")
                        .orElseThrow();

        assertThat(url.dialUrl()).isEqualTo("opc.tcp://localhost:53530/OPCUA/SimulationServer");
    }

    @Test
    void percentEncodedQuestionMarkAndHashStayOnTheDialPath() {
        String endpointUrl = "opc.tcp://localhost:53530/path%3Fkeep%23keep";

        OnboardingProbeUrl url = OnboardingProbeUrl.parse(endpointUrl).orElseThrow();

        assertThat(url.dialUrl()).isEqualTo(endpointUrl);
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
    void queryAndFragmentAreRejectedRatherThanStripped(String endpointUrl) {
        assertThat(OnboardingProbeUrl.parse(endpointUrl)).isEmpty();
    }

    @Test
    void nullRawPathIsAppendedAsEmptyStringNotTheCharactersNull() {
        OnboardingProbeUrl url = OnboardingProbeUrl.parse("opc.tcp://localhost:53530").orElseThrow();

        assertThat(url.dialUrl()).isEqualTo("opc.tcp://localhost:53530").doesNotEndWith("null");
    }

    @Test
    void ipv6HostKeepsBracketsAndDefaultsThePort() {
        OnboardingProbeUrl url =
                OnboardingProbeUrl.parse("opc.tcp://[::1]/OPCUA/SimulationServer").orElseThrow();

        assertThat(url.host()).isEqualTo("[::1]");
        assertThat(url.dialUrl()).isEqualTo("opc.tcp://[::1]:4840/OPCUA/SimulationServer");
    }

    @Test
    void mappedIpv6LiteralIsAccepted() {
        OnboardingProbeUrl url =
                OnboardingProbeUrl.parse("opc.tcp://[::ffff:192.0.2.1]:4840/OPCUA/SimulationServer")
                        .orElseThrow();

        assertThat(url.host()).isEqualTo("[::ffff:192.0.2.1]");
        assertThat(url.port()).isEqualTo(4840);
    }

    @Test
    void hostOfTwoHundredFiftyThreeCharactersIsAcceptedAndTwoHundredFiftyFourIsNot() {
        String accepted = "a".repeat(253);
        String rejected = "a".repeat(254);

        assertThat(OnboardingProbeUrl.parse("opc.tcp://" + accepted + ":4840")).isPresent();
        assertThat(OnboardingProbeUrl.parse("opc.tcp://" + rejected + ":4840")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "opc.tcp://",
                "opc.tcp:localhost:4840",
                "opc.tcp:///OPCUA/SimulationServer",
                "opc.tcp://user:pass@localhost:53530",
                "opc.tcp://host:abc",
                "opc.tcp://localhost:",
                "opc.tcp://[::1]:",
                "opc.tcp://[::1]4840",
                "opc.tcp://[::1]4840/OPCUA/SimulationServer",
                "opc.tcp://localhost:0/OPCUA/SimulationServer",
                "opc.tcp://localhost:70000/OPCUA/SimulationServer",
                "opc.tcp://localhost:+80/OPCUA/SimulationServer",
                "opc.tcp://localhost:0080/OPCUA/SimulationServer",
                "opc.tcp://localhost:\uFF180/OPCUA/SimulationServer",
                "opc.tcp://[hello]:4840",
                "opc.tcp://host:1:2",
                "http://localhost:53530",
                " opc.tcp://localhost:53530",
                "opc.tcp://localhost:53530 ",
                ""
            })
    void rejectedEndpointUrls(String endpointUrl) {
        assertThat(OnboardingProbeUrl.parse(endpointUrl)).isEmpty();
    }

    @Test
    void nullEndpointUrlIsRejectedWithoutThrowing() {
        assertThat(OnboardingProbeUrl.parse(null)).isEmpty();
    }
}
