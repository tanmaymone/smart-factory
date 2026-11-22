package com.factory.edge.web;

import com.factory.edge.command.EdgeCommandHistory;
import com.factory.edge.command.MqttCommandLink;
import com.factory.edge.mqtt.EdgeLinkHealth;
import com.factory.edge.mqtt.MqttProperties;
import com.factory.edge.mqtt.MqttTelemetryLink;
import com.factory.edge.opcua.OpcUaLinkHealth;
import com.factory.edge.opcua.OpcUaProperties;
import com.factory.edge.opcua.OpcUaSession;
import com.factory.edge.opcua.TagObservationStore;
import com.factory.edge.web.EdgeStatusSnapshot.CommandStatus;
import com.factory.edge.web.EdgeStatusSnapshot.MqttStatus;
import com.factory.edge.web.EdgeStatusSnapshot.OpcUaStatus;
import com.factory.edge.web.EdgeStatusSnapshot.TagStatus;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/** Assembles a read-only diagnostics snapshot from existing edge owners. */
@Service
public class EdgeStatusService {

    private final OpcUaProperties opcUaProperties;
    private final OpcUaSession session;
    private final OpcUaLinkHealth opcUaLinkHealth;
    private final TagObservationStore observations;
    private final MqttProperties mqttProperties;
    private final MqttTelemetryLink telemetryLink;
    private final MqttCommandLink commandLink;
    private final EdgeCommandHistory commandHistory;

    public EdgeStatusService(
            OpcUaProperties opcUaProperties,
            OpcUaSession session,
            OpcUaLinkHealth opcUaLinkHealth,
            TagObservationStore observations,
            MqttProperties mqttProperties,
            MqttTelemetryLink telemetryLink,
            MqttCommandLink commandLink,
            EdgeCommandHistory commandHistory) {
        this.opcUaProperties = opcUaProperties;
        this.session = session;
        this.opcUaLinkHealth = opcUaLinkHealth;
        this.observations = observations;
        this.mqttProperties = mqttProperties;
        this.telemetryLink = telemetryLink;
        this.commandLink = commandLink;
        this.commandHistory = commandHistory;
    }

    public EdgeStatusSnapshot snapshot() {
        return new EdgeStatusSnapshot(opcUa(), mqtt(), tags(), commands());
    }

    private OpcUaStatus opcUa() {
        OpcUaLinkHealth.Snapshot health = opcUaLinkHealth.snapshot();
        boolean up = "UP".equals(health.phase()) && session.isAvailable();
        return new OpcUaStatus(
                opcUaProperties.getEndpointUrl(),
                up ? OpcUaLinkHealth.CONNECTED : OpcUaLinkHealth.DISCONNECTED,
                health.phase(),
                health.lastError(),
                health.lastHeartbeatAt());
    }

    private MqttStatus mqtt() {
        EdgeLinkHealth health = telemetryLink.health();
        return new MqttStatus(
                mqttProperties.getBrokerUrl(),
                health.status(),
                commandLink.isConnected(),
                health.bufferedCount(),
                health.lastPublishAt());
    }

    private List<TagStatus> tags() {
        List<OpcUaProperties.TagConfig> configs = opcUaProperties.getTags();
        if (configs == null || configs.isEmpty()) {
            return List.of();
        }
        List<TagStatus> rows = new ArrayList<>(configs.size());
        for (OpcUaProperties.TagConfig tag : configs) {
            TagObservationStore.Observation obs =
                    observations.get(tag.getSiteId(), tag.getLineId(), tag.getTagCode());
            rows.add(
                    new TagStatus(
                            tag.getSiteId(),
                            tag.getLineId(),
                            tag.getTagCode(),
                            tag.getNodeId(),
                            tag.getDeadband(),
                            obs == null ? null : obs.value(),
                            obs == null ? null : obs.quality(),
                            obs == null ? null : obs.observedAt(),
                            obs == null ? null : obs.emitted()));
        }
        return List.copyOf(rows);
    }

    private List<CommandStatus> commands() {
        List<EdgeCommandHistory.Entry> recent = commandHistory.recent();
        List<CommandStatus> rows = new ArrayList<>(recent.size());
        for (EdgeCommandHistory.Entry entry : recent) {
            var result = entry.result();
            rows.add(
                    new CommandStatus(
                            result.commandId(),
                            result.siteId(),
                            result.lineId(),
                            result.tagCode(),
                            result.nodeId(),
                            result.targetValue(),
                            result.status(),
                            result.reason(),
                            result.executedAt(),
                            entry.ackPublished()));
        }
        return List.copyOf(rows);
    }
}
