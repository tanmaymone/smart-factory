package com.factory.edge.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.factory.edge.onboarding.OnboardingProbeResponse.TagDiagnostic;
import com.factory.edge.onboarding.OnboardingProbeService.HostResolver;
import com.factory.edge.opcua.OnboardingOpcUaBridge;
import com.factory.edge.opcua.OpcUaProbeException;
import com.factory.edge.opcua.OpcUaProperties;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("EDGE-PROBE")
class OnboardingProbeServiceTest {

    private static final List<String> SUBMITTED =
            Arrays.asList(null, " ", "ns=0;i=2259", "ns=0;i=2259");

    private final CountDownLatch release = new CountDownLatch(1);
    private final List<OnboardingProbeService> services = new ArrayList<>();
    private final OnboardingOpcUaBridge bridge = mock(OnboardingOpcUaBridge.class);

    @AfterEach
    void releaseWorkers() {
        release.countDown();
        services.forEach(OnboardingProbeService::shutdown);
    }

    @Test
    void dnsFailureIsReachableFalseAndKeepsSubmittedOrder() {
        OnboardingProbeService service =
                newService("", 8_000L, host -> { throw new UnknownHostException(host); });

        OnboardingProbeResponse response = probe(service, "None", SUBMITTED);

        assertThat(response.reachable()).isFalse();
        assertThat(response.error()).contains("DNS resolution failed for host");
        assertThat(response.tags())
                .extracting(TagDiagnostic::nodeId)
                .containsExactly(null, " ", "ns=0;i=2259", "ns=0;i=2259");
        assertThat(response.tags())
                .allSatisfy(
                        tag -> {
                            assertThat(tag.reachability()).isEqualTo("UNREACHABLE");
                            assertThat(tag.quality()).isEqualTo("UNREACHABLE");
                            assertThat(tag.value()).isNull();
                            assertThat(tag.dataType()).isNull();
                            assertThat(tag.statusName()).isNull();
                            assertThat(tag.error()).isNull();
                        });
    }

    @Test
    void omittedNodeIdsYieldEmptyTags() {
        OnboardingProbeService service =
                newService("", 8_000L, host -> { throw new UnknownHostException(host); });

        assertThat(probe(service, "None", null).tags()).isEmpty();
    }

    @Test
    void closedTcpPortIsReachableFalseWithoutThrowing() throws Exception {
        int closedPort = freePort();
        // Returns an endpoint if discovery runs, so the verify below is what fails a late TCP check.
        when(bridge.discoverAndRewriteForProbe(any(), any(), anyLong()))
                .thenReturn(mock(EndpointDescription.class));
        OnboardingProbeService service =
                newService("", 8_000L, host -> InetAddress.getLoopbackAddress());

        OnboardingProbeResponse response =
                service
                        .probe(
                                request("opc.tcp://127.0.0.1:" + closedPort, "None", List.of()),
                                OnboardingProbeUrl.parse("opc.tcp://127.0.0.1:" + closedPort)
                                        .orElseThrow())
                        .orElseThrow();

        assertThat(response.reachable()).isFalse();
        assertThat(response.error()).contains("TCP connect failed");
        verify(bridge, never()).discoverAndRewriteForProbe(any(), any(), anyLong());
        verify(bridge, never()).createForProbe(any(), any(), any(), any(), anyLong());
    }

    @Test
    void handshakeRejectionKeepsTheStatusOnTheEnvelope() throws Exception {
        try (ServerSocket listening = new ServerSocket(0)) {
            String endpointUrl = "opc.tcp://127.0.0.1:" + listening.getLocalPort();
            when(bridge.discoverAndRewriteForProbe(any(), any(), anyLong()))
                    .thenReturn(mock(EndpointDescription.class));
            when(bridge.createForProbe(any(), any(), any(), any(), anyLong()))
                    .thenThrow(new OpcUaProbeException("Bad_SecurityChecksFailed: not trusted"));
            OnboardingProbeService service =
                    newService("", 8_000L, host -> InetAddress.getLoopbackAddress());

            OnboardingProbeResponse response =
                    service
                            .probe(
                                    request(endpointUrl, "None", List.of()),
                                    OnboardingProbeUrl.parse(endpointUrl).orElseThrow())
                            .orElseThrow();

            assertThat(response.reachable()).isFalse();
            assertThat(response.error()).isEqualTo("Bad_SecurityChecksFailed: not trusted");
        }
    }

    @Test
    void signAndEncryptWithUnusablePkiReturnsBeforeDnsAndBeforeAnySubmit() throws Exception {
        when(bridge.pkiFailure("")).thenReturn("app.opcua.security.pki-dir is required");
        OnboardingProbeService service =
                newService("", 8_000L, host -> { throw new AssertionError("DNS must not run"); });

        long startedAt = System.nanoTime();
        OnboardingProbeResponse response = probe(service, "SignAndEncrypt", SUBMITTED);
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;

        assertThat(response.reachable()).isFalse();
        assertThat(response.error()).isEqualTo("app.opcua.security.pki-dir is required");
        assertThat(response.tags()).hasSize(4);
        // Returned from the PKI check, not after waiting out the 8s budget.
        assertThat(elapsedMs).isLessThan(4_000L);
        verify(bridge, never()).discoverAndRewriteForProbe(any(), any(), anyLong());
        assertThat(threadsNamed(service.ioThreadPrefix())).isZero();
    }

    @Test
    void noneDoesNotConsultPkiDir() {
        OnboardingProbeService service =
                newService("", 8_000L, host -> { throw new UnknownHostException(host); });

        probe(service, "None", List.of());

        verify(bridge, never()).pkiFailure(any());
    }

    @Test
    void secondProbeWhileTheLockIsHeldGetsNoResult() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        OnboardingProbeService service =
                newService(
                        "",
                        60_000L,
                        host -> {
                            entered.countDown();
                            awaitRelease();
                            throw new UnknownHostException(host);
                        });

        Thread holder = new Thread(() -> probe(service, "None", List.of()), "probe-holder");
        holder.setDaemon(true);
        holder.start();
        assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();

        assertThat(
                        service.probe(
                                request("opc.tcp://localhost:53530", "None", List.of()),
                                OnboardingProbeUrl.parse("opc.tcp://localhost:53530").orElseThrow()))
                .isEmpty();

        release.countDown();
        holder.join(10_000);
    }

    @Test
    void repeatedTimeoutsSaturateThePoolAndNeverGrowPastTwoWorkers() {
        OnboardingProbeService service =
                newService(
                        "",
                        50L,
                        host -> {
                            awaitRelease();
                            throw new UnknownHostException(host);
                        });

        List<String> errors = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            errors.add(probe(service, "None", List.of()).error());
            if (i == 1) {
                assertThat(threadsNamed(service.ioThreadPrefix())).isEqualTo(2);
            }
        }

        assertThat(errors.subList(0, 2)).containsOnly("probe deadline exceeded");
        assertThat(errors.subList(2, 8)).containsOnly("probe I/O pool saturated");
        // cancel(true) is best-effort only: abandoned lookups are not replaced with new threads.
        assertThat(threadsNamed(service.ioThreadPrefix())).isEqualTo(2);
    }

    @Test
    void interruptReturnsTwoHundredWithTheFlagRestored() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        OnboardingProbeService service =
                newService(
                        "",
                        60_000L,
                        host -> {
                            entered.countDown();
                            awaitRelease();
                            return InetAddress.getLoopbackAddress();
                        });
        AtomicReference<OnboardingProbeResponse> result = new AtomicReference<>();
        AtomicBoolean flagged = new AtomicBoolean();

        Thread probeThread =
                new Thread(
                        () -> {
                            result.set(probe(service, "None", SUBMITTED));
                            flagged.set(Thread.currentThread().isInterrupted());
                        },
                        "probe-interrupt");
        probeThread.setDaemon(true);
        probeThread.start();
        assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
        while (probeThread.getState() != Thread.State.TIMED_WAITING && probeThread.isAlive()) {
            Thread.onSpinWait();
        }
        probeThread.interrupt();
        probeThread.join(10_000);

        assertThat(result.get().reachable()).isFalse();
        assertThat(result.get().error()).isEqualTo("interrupted");
        assertThat(result.get().tags()).hasSize(4);
        assertThat(flagged).isTrue();
    }

    private OnboardingProbeResponse probe(
            OnboardingProbeService service, String securityMode, List<String> nodeIds) {
        String endpointUrl = "opc.tcp://does-not-resolve.invalid:4840/OPCUA/SimulationServer";
        return service
                .probe(
                        request(endpointUrl, securityMode, nodeIds),
                        OnboardingProbeUrl.parse(endpointUrl).orElseThrow())
                .orElseThrow();
    }

    private static OnboardingProbeRequest request(
            String endpointUrl, String securityMode, List<String> nodeIds) {
        return new OnboardingProbeRequest(endpointUrl, securityMode, nodeIds);
    }

    private OnboardingProbeService newService(String pkiDir, long budgetMs, HostResolver resolver) {
        OpcUaProperties properties = new OpcUaProperties();
        properties.getSecurity().setPkiDir(pkiDir);
        OnboardingProbeService service =
                new OnboardingProbeService(bridge, properties, budgetMs, resolver);
        services.add(service);
        return service;
    }

    /** A real hung lookup ignores interruption, so {@code cancel(true)} must not free the worker. */
    private void awaitRelease() {
        boolean interrupted = false;
        while (true) {
            try {
                release.await();
                break;
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static long threadsNamed(String prefix) {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> thread.getName().startsWith(prefix))
                .count();
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
