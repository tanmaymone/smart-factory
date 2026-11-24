package com.factory.edge.opcua;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.factory.edge.buffer.StoreAndForwardQueue;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("EDGE-OPC-PROBE")
class OpcUaProbeFailFastTest {

    private OpcUaProperties properties;
    private OpcUaSession session;
    private StoreAndForwardQueue queue;
    private ScheduledExecutorService scheduler;
    private OpcUaProbe probe;

    @BeforeEach
    void setUp() {
        properties = new OpcUaProperties();
        properties.setEndpointUrl("opc.tcp://localhost:53530/OPCUA/SimulationServer");
        session = mock(OpcUaSession.class);
        queue = mock(StoreAndForwardQueue.class);
        scheduler = mock(ScheduledExecutorService.class);
        probe = new OpcUaProbe(properties, session, queue, scheduler);
    }

    @Test
    void emptyTagListThrowsBeforeConnect() {
        properties.setTags(List.of());

        assertThatThrownBy(probe::start)
                .isInstanceOf(OpcUaProbeException.class)
                .hasMessage("OPC UA tag list is empty");
        verify(session, never()).start(anyList(), any(), any());
        assertThat(probe.isRunning()).isFalse();
    }

    @Test
    void invalidNodeIdThrowsBeforeConnectWhenSessionValidates() {
        OpcUaProperties.TagConfig tag = new OpcUaProperties.TagConfig();
        tag.setSiteId("INDORE");
        tag.setLineId("LINE-01");
        tag.setTagCode("VALVE_101_POS");
        tag.setNodeId("not-a-node");
        properties.setTags(List.of(tag));

        MiloOpcUaSession realSession = new MiloOpcUaSession(properties);
        OpcUaProbe realProbe = new OpcUaProbe(properties, realSession, queue, scheduler);

        assertThatThrownBy(realProbe::start)
                .isInstanceOf(OpcUaProbeException.class)
                .hasMessageContaining("Invalid NodeId")
                .hasMessageContaining("tagCode=VALVE_101_POS");
        assertThat(realProbe.isRunning()).isFalse();
    }

    @Test
    void badInitialReadLeavesProbeNotRunning() {
        OpcUaProperties.TagConfig tag = new OpcUaProperties.TagConfig();
        tag.setSiteId("INDORE");
        tag.setLineId("LINE-01");
        tag.setTagCode("VALVE_101_POS");
        tag.setNodeId("nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=1001");
        properties.setTags(List.of(tag));

        doThrow(new OpcUaProbeException("Initial read Bad for tagCode=VALVE_101_POS status=Bad_NodeIdUnknown"))
                .when(session)
                .start(anyList(), any(), any());

        assertThatThrownBy(probe::start)
                .isInstanceOf(OpcUaProbeException.class)
                .hasMessageContaining("Initial read Bad");
        assertThat(probe.isRunning()).isFalse();
    }

    @Test
    void badMonitoredItemLeavesProbeNotRunning() {
        OpcUaProperties.TagConfig tag = new OpcUaProperties.TagConfig();
        tag.setSiteId("INDORE");
        tag.setLineId("LINE-01");
        tag.setTagCode("VALVE_101_POS");
        tag.setNodeId("nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=1001");
        properties.setTags(List.of(tag));

        doThrow(new OpcUaProbeException("Monitored item Bad for tagCode=VALVE_101_POS status=Bad_NodeIdUnknown"))
                .when(session)
                .start(anyList(), any(), any());

        assertThatThrownBy(probe::start)
                .isInstanceOf(OpcUaProbeException.class)
                .hasMessageContaining("Monitored item Bad");
        verify(session).start(anyList(), any(), any());
        assertThat(probe.isRunning()).isFalse();
    }
}
