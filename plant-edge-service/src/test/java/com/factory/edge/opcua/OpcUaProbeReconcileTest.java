package com.factory.edge.opcua;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.factory.edge.buffer.StoreAndForwardQueue;
import com.factory.edge.command.CommandDispatcher;
import com.factory.edge.config.ActiveTagCatalog;
import com.factory.edge.config.EdgeApplyState;
import com.factory.edge.config.EdgeConfigProperties;
import com.factory.edge.config.EdgeConfigSync;
import com.factory.edge.config.EdgeManifest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** M3.4 OPC UA hot-reload: IDLE / UP / BACKOFF transitions driven by the central manifest. */
@Tag("EDGE-CONFIG")
class OpcUaProbeReconcileTest {

    private static final String VALVE = "nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=1007";
    private static final String TEMP = "nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=1002";

    private final ObjectMapper mapper = new ObjectMapper();

    private OpcUaProperties properties;
    private OpcUaSession session;
    private EdgeConfigSync sync;
    private ActiveTagCatalog catalog;
    private EdgeApplyState applyState;
    private CommandDispatcher dispatcher;
    private TagObservationStore observations;
    private RecordingScheduler scheduler;
    private OpcUaProbe probe;

    @BeforeEach
    void setUp() {
        properties = new OpcUaProperties();
        properties.setEndpointUrl("opc.tcp://localhost:53530/OPCUA/SimulationServer");
        properties.setReconnectInitialMs(1000);
        properties.setReconnectMaxMs(30000);

        session = mock(OpcUaSession.class);
        sync = mock(EdgeConfigSync.class);
        catalog = new ActiveTagCatalog();
        applyState = new EdgeApplyState();
        dispatcher = mock(CommandDispatcher.class);
        observations = new TagObservationStore(catalog);
        scheduler = new RecordingScheduler();

        EdgeConfigProperties edgeConfig = new EdgeConfigProperties();
        edgeConfig.setConfigSyncEnabled(true);
        edgeConfig.setSiteId("INDORE");

        probe =
                new OpcUaProbe(
                        properties,
                        session,
                        mock(StoreAndForwardQueue.class),
                        new OpcUaDiagnosticsSink(new OpcUaLinkHealth(), observations),
                        scheduler,
                        false,
                        new OpcUaProbe.SyncDeps(
                                edgeConfig, sync, catalog, applyState, dispatcher));
    }

    @Test
    void startsIdleWithoutDialingUntilAManifestArrives() {
        probe.start();

        assertThat(probe.currentPhase()).isEqualTo(OpcUaProbe.Phase.IDLE);
        assertThat(probe.isRunning()).isTrue();
        assertThat(probe.linkHealth().snapshot().phase()).isEqualTo("IDLE");
        assertThat(scheduler.scheduleCount).isZero();
        verify(session, never()).startLenient(anyList(), any(), any());
        assertThat(applyState.get().activeTagCount()).isZero();
    }

    @Test
    void firstManifestStartsLenientAndPublishesTheAcceptedSet() {
        desire(1, "hash-1", tag("LINE-01", "VALVE_101_POS", VALVE, 0.5, true));
        accept(tag("LINE-01", "VALVE_101_POS", VALVE, 0.5, true));

        probe.start();

        assertThat(probe.currentPhase()).isEqualTo(OpcUaProbe.Phase.UP);
        assertThat(catalog.contains("INDORE", "LINE-01", "VALVE_101_POS")).isTrue();
        assertThat(applyState.get().appliedVersion()).isEqualTo(1L);
        assertThat(applyState.get().appliedContentHash()).isEqualTo("hash-1");
        assertThat(applyState.get().activeTagCount()).isEqualTo(1);
        assertThat(applyState.get().rejectedCount()).isZero();

        ArgumentCaptor<List<OpcUaProperties.TagConfig>> captor = listCaptor();
        verify(dispatcher).replaceConfiguredTags(captor.capture());
        assertThat(captor.getValue()).singleElement().satisfies(t -> {
            assertThat(t.getTagCode()).isEqualTo("VALVE_101_POS");
            assertThat(t.isWritable()).isTrue();
        });
    }

    @Test
    void configRejectedTagNeverReachesTheServerButIsReported() {
        OpcUaProperties.TagConfig bad = tag("LINE-01", "BAD_ADDR", "ns=3;i=7", 0.0, true);
        desire(2, "hash-2", tag("LINE-01", "VALVE_101_POS", VALVE, 0.5, true), bad);
        accept(tag("LINE-01", "VALVE_101_POS", VALVE, 0.5, true));

        probe.start();

        ArgumentCaptor<List<OpcUaProperties.TagConfig>> captor = listCaptor();
        verify(session).startLenient(captor.capture(), any(), any());
        assertThat(captor.getValue())
                .extracting(OpcUaProperties.TagConfig::getTagCode)
                .containsExactly("VALVE_101_POS");
        assertThat(applyState.get().activeTagCount()).isEqualTo(1);
        assertThat(applyState.get().rejected())
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.tagCode()).isEqualTo("BAD_ADDR");
                    assertThat(r.reason()).isEqualTo(OpcUaApply.INVALID_NODE_ID);
                });
    }

    @Test
    void everyTagRejectedLeavesTheEdgeIdleWithoutBackoff() {
        desire(3, "hash-3", tag("LINE-01", "BAD_ADDR", "ns=3;i=7", 0.0, true));

        probe.start();

        assertThat(probe.currentPhase()).isEqualTo(OpcUaProbe.Phase.IDLE);
        assertThat(scheduler.scheduleCount).isZero();
        verify(session, never()).startLenient(anyList(), any(), any());
        assertThat(applyState.get().activeTagCount()).isZero();
        assertThat(applyState.get().rejectedCount()).isEqualTo(1);
    }

    @Test
    void emptyManifestWhileUpClosesOnPurposeAndStaysIdle() {
        desire(1, "hash-1", tag("LINE-01", "VALVE_101_POS", VALVE, 0.5, true));
        accept(tag("LINE-01", "VALVE_101_POS", VALVE, 0.5, true));
        probe.start();
        assertThat(probe.currentPhase()).isEqualTo(OpcUaProbe.Phase.UP);

        desire(2, "hash-2");
        probe.requestReconcile();

        assertThat(probe.currentPhase()).isEqualTo(OpcUaProbe.Phase.IDLE);
        assertThat(scheduler.scheduleCount).isZero();
        assertThat(catalog.size()).isZero();
        assertThat(applyState.get().activeTagCount()).isZero();
        assertThat(applyState.get().appliedVersion()).isEqualTo(2L);
        verify(session, atLeastOnce()).close();
        verify(dispatcher).replaceConfiguredTags(List.of());
    }

    @Test
    void unavailableServerBacksOffThenRecoversOnTheScheduledAttempt() {
        desire(1, "hash-1", tag("LINE-01", "VALVE_101_POS", VALVE, 0.5, true));
        doThrow(new OpcUaUnavailableException("OPC UA discovery failed: Connection refused"))
                .when(session)
                .startLenient(anyList(), any(), any());

        probe.start();

        assertThat(probe.currentPhase()).isEqualTo(OpcUaProbe.Phase.BACKOFF);
        assertThat(scheduler.scheduleCount).isEqualTo(1);
        assertThat(scheduler.lastDelayMs).isEqualTo(1000L);
        assertThat(probe.linkHealth().snapshot().lastError())
                .contains("OPC UA server is not live at");
        assertThat(catalog.size()).isZero();

        accept(tag("LINE-01", "VALVE_101_POS", VALVE, 0.5, true));
        scheduler.runLastTask();

        assertThat(probe.currentPhase()).isEqualTo(OpcUaProbe.Phase.UP);
        assertThat(probe.currentBackoffMs()).isEqualTo(1000L);
        assertThat(catalog.size()).isEqualTo(1);
    }

    @Test
    void repeatedBackoffDoublesTheDelayOnce() {
        desire(1, "hash-1", tag("LINE-01", "VALVE_101_POS", VALVE, 0.5, true));
        doThrow(new OpcUaUnavailableException("Connection refused"))
                .when(session)
                .startLenient(anyList(), any(), any());

        probe.start();
        assertThat(scheduler.lastDelayMs).isEqualTo(1000L);

        scheduler.runLastTask();

        assertThat(probe.currentPhase()).isEqualTo(OpcUaProbe.Phase.BACKOFF);
        assertThat(probe.currentBackoffMs()).isEqualTo(2000L);
        assertThat(scheduler.lastDelayMs).isEqualTo(2000L);
    }

    @Test
    void identicalManifestIsANoOp() {
        desire(1, "hash-1", tag("LINE-01", "VALVE_101_POS", VALVE, 0.5, true));
        accept(tag("LINE-01", "VALVE_101_POS", VALVE, 0.5, true));
        probe.start();

        probe.requestReconcile();
        probe.requestReconcile();

        verify(session, times(1)).startLenient(anyList(), any(), any());
        verify(session, never()).prepareSwap(anyList(), anySet(), any());
    }

    @Test
    void deadbandOnlyChangeSkipsTheSubscriptionSwap() {
        desire(1, "hash-1", tag("LINE-01", "VALVE_101_POS", VALVE, 0.5, true));
        accept(tag("LINE-01", "VALVE_101_POS", VALVE, 0.5, true));
        probe.start();

        desire(2, "hash-2", tag("LINE-01", "VALVE_101_POS", VALVE, 1.5, false));
        probe.requestReconcile();

        verify(session, never()).prepareSwap(anyList(), anySet(), any());
        verify(session, never()).deactivateReadings();
        assertThat(probe.currentPhase()).isEqualTo(OpcUaProbe.Phase.UP);
        assertThat(applyState.get().appliedVersion()).isEqualTo(2L);
        assertThat(catalog.tags())
                .singleElement()
                .satisfies(t -> {
                    assertThat(t.getDeadband()).isEqualTo(1.5);
                    assertThat(t.isWritable()).isFalse();
                });
    }

    @Test
    void reAddressedTagSwapsTheSubscriptionAndUnseedsOnlyThatTag() {
        desire(
                1,
                "hash-1",
                tag("LINE-01", "VALVE_101_POS", VALVE, 0.5, true),
                tag("LINE-01", "REACTOR_TEMP_C", TEMP, 0.2, false));
        accept(
                tag("LINE-01", "VALVE_101_POS", VALVE, 0.5, true),
                tag("LINE-01", "REACTOR_TEMP_C", TEMP, 0.2, false));
        probe.start();

        OpcUaProperties.TagConfig moved = tag("LINE-01", "REACTOR_TEMP_C", VALVE, 0.2, false);
        desire(2, "hash-2", tag("LINE-01", "VALVE_101_POS", VALVE, 0.5, true), moved);
        when(session.prepareSwap(anyList(), anySet(), any()))
                .thenReturn(
                        new FakePlan(
                                List.of(tag("LINE-01", "VALVE_101_POS", VALVE, 0.5, true), moved),
                                List.of()));

        probe.requestReconcile();

        ArgumentCaptor<java.util.Set<String>> reseed = setCaptor();
        verify(session).prepareSwap(anyList(), reseed.capture(), any());
        assertThat(reseed.getValue()).containsExactly("INDORE|LINE-01|REACTOR_TEMP_C");

        var order = org.mockito.Mockito.inOrder(session);
        order.verify(session).prepareSwap(anyList(), anySet(), any());
        order.verify(session).deactivateReadings();
        order.verify(session).commitSwap(any());
        assertThat(probe.currentPhase()).isEqualTo(OpcUaProbe.Phase.UP);
        assertThat(applyState.get().appliedContentHash()).isEqualTo("hash-2");
    }

    @Test
    void swapThatRejectsEveryTagAbortsAndFallsBackToIdle() {
        desire(1, "hash-1", tag("LINE-01", "VALVE_101_POS", VALVE, 0.5, true));
        accept(tag("LINE-01", "VALVE_101_POS", VALVE, 0.5, true));
        probe.start();

        desire(2, "hash-2", tag("LINE-01", "VALVE_101_POS", TEMP, 0.5, true));
        when(session.prepareSwap(anyList(), anySet(), any()))
                .thenReturn(
                        new FakePlan(
                                List.of(),
                                List.of(
                                        new OpcUaApply.Reject(
                                                "LINE-01",
                                                "VALVE_101_POS",
                                                TEMP,
                                                OpcUaApply.BAD_MONITORED_ITEM))));

        probe.requestReconcile();

        verify(session).abortSwap(any());
        verify(session, never()).commitSwap(any());
        assertThat(probe.currentPhase()).isEqualTo(OpcUaProbe.Phase.IDLE);
        assertThat(scheduler.scheduleCount).isZero();
        assertThat(applyState.get().activeTagCount()).isZero();
        assertThat(applyState.get().rejectedCount()).isEqualTo(1);
    }

    @Test
    void sessionLossWithEmptyDesiredGoesIdleWithoutScheduling() {
        desire(1, "hash-1", tag("LINE-01", "VALVE_101_POS", VALVE, 0.5, true));
        accept(tag("LINE-01", "VALVE_101_POS", VALVE, 0.5, true));
        ArgumentCaptor<java.util.function.LongConsumer> lost =
                ArgumentCaptor.forClass(java.util.function.LongConsumer.class);
        probe.start();
        verify(session).startLenient(anyList(), any(), lost.capture());

        desire(2, "hash-2");
        lost.getValue().accept(1L);

        assertThat(probe.currentPhase()).isEqualTo(OpcUaProbe.Phase.IDLE);
        assertThat(scheduler.scheduleCount).isZero();
    }

    @Test
    void sessionLossWithLiveDesiredStillBacksOff() {
        desire(1, "hash-1", tag("LINE-01", "VALVE_101_POS", VALVE, 0.5, true));
        accept(tag("LINE-01", "VALVE_101_POS", VALVE, 0.5, true));
        ArgumentCaptor<java.util.function.LongConsumer> lost =
                ArgumentCaptor.forClass(java.util.function.LongConsumer.class);
        probe.start();
        verify(session).startLenient(anyList(), any(), lost.capture());

        lost.getValue().accept(1L);

        assertThat(probe.currentPhase()).isEqualTo(OpcUaProbe.Phase.BACKOFF);
        assertThat(scheduler.scheduleCount).isEqualTo(1);
        assertThat(scheduler.lastDelayMs).isEqualTo(1000L);
    }

    private void desire(long version, String hash, OpcUaProperties.TagConfig... tags) {
        when(sync.desiredOrNull()).thenReturn(manifest(version, hash, List.of(tags)));
    }

    /** doReturn so re-stubbing after a doThrow does not call the throwing stub. */
    private void accept(OpcUaProperties.TagConfig... tags) {
        doReturn(new OpcUaApply.Result(List.of(tags), List.of()))
                .when(session)
                .startLenient(anyList(), any(), any());
    }

    private EdgeManifest manifest(long version, String hash, List<OpcUaProperties.TagConfig> tags) {
        return new EdgeManifest("INDORE", "ACTIVE", version, hash, List.copyOf(tags), "{}");
    }

    private static OpcUaProperties.TagConfig tag(
            String lineId, String tagCode, String address, double deadband, boolean writable) {
        OpcUaProperties.TagConfig config = new OpcUaProperties.TagConfig();
        config.setSiteId("INDORE");
        config.setLineId(lineId);
        config.setTagCode(tagCode);
        config.setNodeId(address);
        config.setDeadband(deadband);
        config.setWritable(writable);
        return config;
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<List<OpcUaProperties.TagConfig>> listCaptor() {
        return ArgumentCaptor.forClass(List.class);
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<java.util.Set<String>> setCaptor() {
        return ArgumentCaptor.forClass(java.util.Set.class);
    }

    private record FakePlan(
            List<OpcUaProperties.TagConfig> accepted, List<OpcUaApply.Reject> rejected)
            implements OpcUaSession.SwapPlan {}
}
