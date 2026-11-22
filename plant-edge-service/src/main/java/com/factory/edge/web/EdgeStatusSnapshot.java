package com.factory.edge.web;

import java.time.Instant;
import java.util.List;

/** Aggregated edge diagnostics payload for {@code GET /api/edge/status}. */
public record EdgeStatusSnapshot(
        OpcUaStatus opcua,
        MqttStatus mqtt,
        List<TagStatus> tags,
        List<CommandStatus> commands) {

    public record OpcUaStatus(
            String endpointUrl,
            String status,
            String phase,
            String lastError,
            Instant lastHeartbeatAt) {}

    public record MqttStatus(
            String brokerUrl,
            String telemetryStatus,
            boolean commandSubscriberConnected,
            int bufferedCount,
            Instant lastPublishAt) {}

    public record TagStatus(
            String siteId,
            String lineId,
            String tagCode,
            String nodeId,
            double deadband,
            Object value,
            String quality,
            Instant observedAt,
            Boolean emitted) {}

    public record CommandStatus(
            String commandId,
            String siteId,
            String lineId,
            String tagCode,
            String nodeId,
            Object targetValue,
            String status,
            String reason,
            Instant executedAt,
            boolean ackPublished) {}
}
