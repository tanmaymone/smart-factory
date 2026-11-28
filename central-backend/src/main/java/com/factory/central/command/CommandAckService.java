package com.factory.central.command;

import com.factory.central.stream.CommandUpdateEvent;
import com.factory.central.stream.CommandStreamSink;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CommandAckService {

    private static final Logger log = LoggerFactory.getLogger(CommandAckService.class);

    private final CommandAuditLogRepository auditLogRepository;
    private final CommandAuditWriter auditWriter;
    private final CommandStreamSink commandStreamSink;
    private final Clock clock;

    public CommandAckService(
            CommandAuditLogRepository auditLogRepository,
            CommandAuditWriter auditWriter,
            CommandStreamSink commandStreamSink,
            Clock clock) {
        this.auditLogRepository = auditLogRepository;
        this.auditWriter = auditWriter;
        this.commandStreamSink = commandStreamSink;
        this.clock = clock;
    }

    public void applyAck(
            String topicSiteId,
            String topicLineId,
            UUID commandId,
            String payloadSiteId,
            String payloadLineId,
            String status,
            Object appliedValue,
            String errorMessage,
            Instant executedAt) {
        if (commandId == null) {
            log.warn("Ignoring ACK with missing commandId");
            return;
        }
        if (!topicSiteId.equals(payloadSiteId) || !topicLineId.equals(payloadLineId)) {
            log.warn(
                    "Ignoring ACK commandId={} topic={}/{} payload={}/{}",
                    commandId,
                    topicSiteId,
                    topicLineId,
                    payloadSiteId,
                    payloadLineId);
            return;
        }
        CommandStatus next;
        String rejectionReason = null;
        if ("ACKED".equals(status)) {
            next = CommandStatus.ACKED;
        } else if ("FAILED_EDGE".equals(status)) {
            next = CommandStatus.FAILED_EDGE;
            rejectionReason =
                    errorMessage == null || errorMessage.isBlank() ? "Edge command failed" : errorMessage;
        } else if ("REJECTED".equals(status)) {
            next = CommandStatus.FAILED_EDGE;
            rejectionReason =
                    errorMessage == null || errorMessage.isBlank() ? "Edge rejected command" : errorMessage;
        } else {
            log.warn("Ignoring ACK commandId={} with unsupported status={}", commandId, status);
            return;
        }

        Optional<CommandAuditLog> existing = auditLogRepository.findByCommandId(commandId);
        if (existing.isEmpty()) {
            log.warn("Ignoring ACK for unknown commandId={}", commandId);
            return;
        }

        Instant ackedAt = executedAt != null ? executedAt : clock.instant();
        String applied = appliedValue == null ? null : String.valueOf(appliedValue);
        int updated = auditWriter.transitionFromDispatched(commandId, next, applied, rejectionReason, ackedAt);
        if (updated != 1) {
            log.info("ACK ignored for commandId={} status={} (no transition)", commandId, next);
            return;
        }
        CommandAuditLog row = auditWriter.require(commandId);
        commandStreamSink.publishCommand(new CommandUpdateEvent(
                row.getCommandId().toString(),
                row.getSiteId(),
                row.getLineId(),
                row.getTagCode(),
                next.asString(),
                appliedValue,
                rejectionReason,
                ackedAt));
    }
}
