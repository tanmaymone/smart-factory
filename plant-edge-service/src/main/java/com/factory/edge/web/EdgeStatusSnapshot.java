package com.factory.edge.web;

import com.factory.edge.config.EdgeApplyView;
import java.time.Instant;
import java.util.List;

/** Aggregated edge diagnostics payload for {@code GET /api/edge/status}. */
public record EdgeStatusSnapshot(
        OpcUaStatus opcua,
        MqttStatus mqtt,
        ConfigStatus config,
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
            Instant lastPublishAt,
            boolean tlsEnabled,
            String lastError) {}

    /** Sync-owned fetch state plus apply-owned active/rejected state for the central manifest. */
    public record ConfigStatus(
            boolean syncEnabled,
            String siteId,
            String source,
            Long fetchedVersion,
            String contentHash,
            Instant lastSyncedAt,
            Instant lastAttemptAt,
            String lastError,
            Long appliedVersion,
            String appliedContentHash,
            Instant lastAppliedAt,
            int activeTagCount,
            int rejectedCount,
            List<EdgeApplyView.RejectedTag> rejected) {}

    public record TagStatus(
            String siteId,
            String lineId,
            String tagCode,
            String nodeId,
            double deadband,
            boolean writable,
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
