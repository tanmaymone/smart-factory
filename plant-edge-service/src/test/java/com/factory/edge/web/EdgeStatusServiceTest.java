package com.factory.edge.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.factory.edge.command.CommandResult;
import com.factory.edge.command.EdgeCommandHistory;
import com.factory.edge.command.MqttCommandLink;
import com.factory.edge.mqtt.EdgeLinkHealth;
import com.factory.edge.mqtt.MqttProperties;
import com.factory.edge.mqtt.MqttTelemetryLink;
import com.factory.edge.opcua.OpcUaLinkHealth;
import com.factory.edge.opcua.OpcUaProperties;
import com.factory.edge.opcua.OpcUaSession;
import com.factory.edge.opcua.TagObservationStore;
import com.factory.edge.opcua.TagReading;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("EDGE-UI")
class EdgeStatusServiceTest {

    private OpcUaProperties opcUaProperties;
    private OpcUaSession session;
    private OpcUaLinkHealth opcHealth;
    private TagObservationStore observations;
    private MqttProperties mqttProperties;
    private MqttTelemetryLink telemetryLink;
    private MqttCommandLink commandLink;
    private EdgeCommandHistory history;
    private EdgeStatusService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        opcUaProperties = new OpcUaProperties();
        opcUaProperties.setEndpointUrl("opc.tcp://localhost:53530/OPCUA/SimulationServer");
        OpcUaProperties.TagConfig indore = tag("INDORE", "LINE-01", "VALVE_101_POS", "ns=3;i=1001", 0.5);
        OpcUaProperties.TagConfig pune = tag("PUNE", "LINE-01", "VALVE_101_POS", "ns=3;i=1001", 0.5);
        opcUaProperties.setTags(List.of(indore, pune));

        session = mock(OpcUaSession.class);
        opcHealth = new OpcUaLinkHealth();
        observations = new TagObservationStore();
        mqttProperties = new MqttProperties();
        mqttProperties.setBrokerUrl("tcp://localhost:1883");
        telemetryLink = mock(MqttTelemetryLink.class);
        commandLink = mock(MqttCommandLink.class);
        history = new EdgeCommandHistory();

        service =
                new EdgeStatusService(
                        opcUaProperties,
                        session,
                        opcHealth,
                        observations,
                        mqttProperties,
                        telemetryLink,
                        commandLink,
                        history);

        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Test
    void disconnectThenReconnectReflectsBufferAndFlush() throws Exception {
        Instant frozenFlush = Instant.parse("2026-01-15T10:00:00Z");
        when(telemetryLink.health())
                .thenReturn(new EdgeLinkHealth(EdgeLinkHealth.DISCONNECTED, 3, frozenFlush));
        when(commandLink.isConnected()).thenReturn(false);
        when(session.isAvailable()).thenReturn(false);

        EdgeStatusSnapshot down = service.snapshot();
        assertThat(down.mqtt().telemetryStatus()).isEqualTo(EdgeLinkHealth.DISCONNECTED);
        assertThat(down.mqtt().bufferedCount()).isEqualTo(3);
        assertThat(down.mqtt().lastPublishAt()).isEqualTo(frozenFlush);
        assertThat(down.mqtt().commandSubscriberConnected()).isFalse();
        assertThat(down.opcua().status()).isEqualTo(OpcUaLinkHealth.DISCONNECTED);
        assertThat(down.opcua().lastHeartbeatAt()).isNull();

        JsonNode downJson = objectMapper.valueToTree(down);
        assertThat(downJson.path("opcua").path("lastHeartbeatAt").isNull()).isTrue();

        Instant flushed = Instant.parse("2026-01-15T10:01:00Z");
        when(telemetryLink.health())
                .thenReturn(new EdgeLinkHealth(EdgeLinkHealth.CONNECTED, 0, flushed));
        when(commandLink.isConnected()).thenReturn(true);
        when(session.isAvailable()).thenReturn(true);
        opcHealth.markUp();

        EdgeStatusSnapshot up = service.snapshot();
        assertThat(up.mqtt().telemetryStatus()).isEqualTo(EdgeLinkHealth.CONNECTED);
        assertThat(up.mqtt().bufferedCount()).isEqualTo(0);
        assertThat(up.mqtt().lastPublishAt()).isEqualTo(flushed);
        assertThat(up.mqtt().commandSubscriberConnected()).isTrue();
        assertThat(up.opcua().status()).isEqualTo(OpcUaLinkHealth.CONNECTED);
        assertThat(up.opcua().phase()).isEqualTo("UP");
    }

    @Test
    void tagsKeepSiteLinePairingAndNativeIntegerValue() throws Exception {
        when(telemetryLink.health())
                .thenReturn(new EdgeLinkHealth(EdgeLinkHealth.CONNECTED, 0, null));
        when(commandLink.isConnected()).thenReturn(true);
        when(session.isAvailable()).thenReturn(true);

        observations.record(
                new TagReading(
                        TagReading.EVENT_READ,
                        "INDORE",
                        "LINE-01",
                        "VALVE_101_POS",
                        "ns=3;i=1001",
                        42,
                        "GOOD",
                        Instant.parse("2026-01-15T10:00:00Z"),
                        Instant.parse("2026-01-15T10:00:00Z"),
                        null),
                true);

        EdgeStatusSnapshot snap = service.snapshot();
        assertThat(snap.tags()).hasSize(2);
        assertThat(snap.tags().get(0).siteId()).isEqualTo("INDORE");
        assertThat(snap.tags().get(0).nodeId()).isEqualTo("ns=3;i=1001");
        assertThat(snap.tags().get(0).value()).isEqualTo(42);
        assertThat(snap.tags().get(0).emitted()).isTrue();
        assertThat(snap.tags().get(1).siteId()).isEqualTo("PUNE");
        assertThat(snap.tags().get(1).value()).isNull();
        assertThat(snap.tags().get(1).emitted()).isNull();

        JsonNode json = objectMapper.valueToTree(snap);
        assertThat(json.path("mqtt").path("lastPublishAt").isNull()).isTrue();
        assertThat(json.path("tags").get(0).path("value").isInt()).isTrue();
        assertThat(json.path("tags").get(0).path("value").asInt()).isEqualTo(42);
    }

    @Test
    void emptyTagListYieldsEmptyArray() {
        opcUaProperties.setTags(List.of());
        when(telemetryLink.health())
                .thenReturn(new EdgeLinkHealth(EdgeLinkHealth.DISCONNECTED, 0, null));
        when(commandLink.isConnected()).thenReturn(false);
        when(session.isAvailable()).thenReturn(false);

        assertThat(service.snapshot().tags()).isEmpty();
    }

    @Test
    void commandHistoryMapsRecentEntries() {
        when(telemetryLink.health())
                .thenReturn(new EdgeLinkHealth(EdgeLinkHealth.CONNECTED, 0, Instant.now()));
        when(commandLink.isConnected()).thenReturn(true);
        when(session.isAvailable()).thenReturn(true);

        Instant at = Instant.parse("2026-01-15T11:00:00Z");
        history.record(
                CommandResult.acked(
                        "cmd-1",
                        "INDORE",
                        "LINE-01",
                        "REACTOR_TEMP_SP",
                        "ns=3;i=1003",
                        72.5,
                        72.5,
                        at),
                true);

        EdgeStatusSnapshot snap = service.snapshot();
        assertThat(snap.commands()).hasSize(1);
        assertThat(snap.commands().get(0).commandId()).isEqualTo("cmd-1");
        assertThat(snap.commands().get(0).status()).isEqualTo("ACKED");
        assertThat(snap.commands().get(0).targetValue()).isEqualTo(72.5);
        assertThat(snap.commands().get(0).ackPublished()).isTrue();
        assertThat(snap.commands().get(0).executedAt()).isEqualTo(at);
    }

    private static OpcUaProperties.TagConfig tag(
            String site, String line, String code, String nodeId, double deadband) {
        OpcUaProperties.TagConfig t = new OpcUaProperties.TagConfig();
        t.setSiteId(site);
        t.setLineId(line);
        t.setTagCode(code);
        t.setNodeId(nodeId);
        t.setDeadband(deadband);
        return t;
    }
}
