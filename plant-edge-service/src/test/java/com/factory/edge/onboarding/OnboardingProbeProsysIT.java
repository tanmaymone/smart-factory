package com.factory.edge.onboarding;

import static org.assertj.core.api.Assertions.assertThat;

import com.factory.edge.onboarding.OnboardingProbeResponse.TagDiagnostic;
import com.factory.edge.opcua.OnboardingOpcUaBridge;
import com.factory.edge.opcua.OpcUaProperties;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Protocol tests against the Prosys simulator with {@code app.onboarding.probe-enabled=true}. CI may
 * {@code Assumptions}-skip these when port 53530 is down; that skip is not a CI failure and it does
 * not sign off M3.3. The combined call is the only {@code EDGE-PROBE-M33} method, and the milestone
 * evidence is a recorded pass of it with Prosys up.
 */
@Tag("EDGE-PROBE")
class OnboardingProbeProsysIT {

    private static final String ENDPOINT = "opc.tcp://localhost:53530/OPCUA/SimulationServer";
    private static final String NS = "nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;";
    private static final String COUNTER = NS + "i=1001";
    private static final String RANDOM = NS + "i=1002";
    private static final String MISSING = NS + "i=999999";

    private OnboardingProbeService service;

    @BeforeEach
    void requireProsys() {
        Assumptions.assumeTrue(
                prosysAcceptingTcp(), "Prosys is not accepting TCP on localhost:53530");
        service =
                new OnboardingProbeService(
                        new OnboardingOpcUaBridge(),
                        new OpcUaProperties(),
                        8_000L,
                        InetAddress::getByName);
    }

    @AfterEach
    void shutdownProbeIo() {
        if (service != null) {
            service.shutdown();
        }
    }

    @Test
    void emptyNodeIdsIsTheStepTwoReachabilityProbe() {
        OnboardingProbeResponse response = probe(ENDPOINT, List.of());

        assertThat(response.reachable()).isTrue();
        // error null is the assertion that Server_NamespaceArray was read.
        assertThat(response.error()).isNull();
        assertThat(response.tags()).isEmpty();
    }

    @Test
    void uppercaseSchemeConnectsAndEchoesTheSubmittedSpelling() {
        String submitted = "OPC.TCP://localhost:53530/OPCUA/SimulationServer";

        OnboardingProbeResponse response = probe(submitted, List.of());

        assertThat(response.reachable()).isTrue();
        assertThat(response.error()).isNull();
        assertThat(response.tags()).isEmpty();
        assertThat(response.endpointUrl()).isEqualTo(submitted);
    }

    @Test
    void liveNodesReadGoodValuesAndTypes() {
        List<TagDiagnostic> tags = probe(ENDPOINT, List.of(COUNTER, RANDOM)).tags();

        assertThat(tags).hasSize(2);
        assertThat(tags.get(0).quality()).isEqualTo("GOOD");
        assertThat(tags.get(0).value()).isNotNull();
        assertThat(tags.get(0).dataType()).isEqualTo("Int32");
        assertThat(tags.get(1).quality()).isEqualTo("GOOD");
        assertThat(tags.get(1).value()).isNotNull();
        assertThat(tags.get(1).dataType()).isEqualTo("Double");
    }

    @Test
    void missingNodeIsADiagnosticNotAFailure() {
        OnboardingProbeResponse response = probe(ENDPOINT, List.of(MISSING));

        assertThat(response.reachable()).isTrue();
        assertThat(response.tags()).hasSize(1);
        assertThat(response.tags().get(0).quality()).isEqualTo("BAD");
        assertThat(response.tags().get(0).statusName()).isEqualTo("Bad_NodeIdUnknown");
    }

    @Test
    @Tag("EDGE-PROBE-M33")
    void combinedCounterRandomAndMissingNode() {
        OnboardingProbeResponse response = probe(ENDPOINT, List.of(COUNTER, RANDOM, MISSING));

        assertThat(response.reachable()).isTrue();
        assertThat(response.tags()).hasSize(3);
        assertThat(response.tags())
                .extracting(TagDiagnostic::nodeId)
                .containsExactly(COUNTER, RANDOM, MISSING);

        TagDiagnostic counter = response.tags().get(0);
        assertThat(counter.reachability()).isEqualTo("REACHABLE");
        assertThat(counter.quality()).isEqualTo("GOOD");
        assertThat(counter.dataType()).isEqualTo("Int32");
        assertThat(counter.value()).isNotNull();

        TagDiagnostic random = response.tags().get(1);
        assertThat(random.quality()).isEqualTo("GOOD");
        assertThat(random.dataType()).isEqualTo("Double");
        assertThat(random.value()).isNotNull();

        TagDiagnostic missing = response.tags().get(2);
        assertThat(missing.quality()).isEqualTo("BAD");
        assertThat(missing.statusName()).isEqualTo("Bad_NodeIdUnknown");
        assertThat(missing.value()).isNull();
    }

    @Test
    void aSecondProbeStillSucceedsBecauseTeardownOnlyDisconnects() {
        assertThat(probe(ENDPOINT, List.of(COUNTER)).tags().get(0).quality()).isEqualTo("GOOD");

        // No Stack.releaseSharedResources(): the shared stack must survive the first probe.
        assertThat(probe(ENDPOINT, List.of(COUNTER)).tags().get(0).quality()).isEqualTo("GOOD");
    }

    private OnboardingProbeResponse probe(String endpointUrl, List<String> nodeIds) {
        OnboardingProbeRequest request =
                new OnboardingProbeRequest(endpointUrl, "None", nodeIds);
        OnboardingProbeValidator.Validation validation = OnboardingProbeValidator.validate(request);
        assertThat(validation.rejected()).isFalse();
        return service.probe(request, validation.url()).orElseThrow();
    }

    private static boolean prosysAcceptingTcp() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", 53530), 1_000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
