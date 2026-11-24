package com.factory.central.command;

import com.factory.central.registry.TagMapping;
import com.factory.central.stream.CommandUpdateEvent;
import com.factory.central.stream.TelemetryStreamHub;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CommandRejectionRecorder {

    private final CommandAuditWriter auditWriter;
    private final TelemetryStreamHub streamHub;

    public CommandRejectionRecorder(CommandAuditWriter auditWriter, TelemetryStreamHub streamHub) {
        this.auditWriter = auditWriter;
        this.streamHub = streamHub;
    }

    public void rejectAndThrow(
            TagMapping tag,
            CommandRequest request,
            String requestedBy,
            String previousValue,
            Instant now,
            CommandStatus status,
            CommandException ex) {
        rejectAndThrow(
                tag.getSiteId(),
                tag.getLineId(),
                tag.getTagCode(),
                request,
                requestedBy,
                previousValue,
                now,
                status,
                ex);
    }

    public void rejectUnknownTagAndThrow(
            CommandRequest request, String requestedBy, Instant now, CommandException ex) {
        String tagCode = request.tagCode() == null || request.tagCode().isBlank()
                ? String.valueOf(request.tagId())
                : request.tagCode().trim();
        rejectAndThrow(
                request.siteId(),
                request.lineId(),
                tagCode,
                request,
                requestedBy,
                null,
                now,
                CommandStatus.REJECTED_VALIDATION,
                ex);
    }

    private void rejectAndThrow(
            String siteId,
            String lineId,
            String tagCode,
            CommandRequest request,
            String requestedBy,
            String previousValue,
            Instant now,
            CommandStatus status,
            CommandException ex) {
        UUID commandId = UUID.randomUUID();
        CommandAuditLog audit = auditWriter.insert(new CommandAuditLog(
                commandId,
                siteId,
                lineId,
                tagCode,
                stringifyTarget(request),
                previousValue,
                requestedBy,
                request.reason().trim(),
                status,
                ex.getMessage(),
                now));
        streamHub.publishCommand(new CommandUpdateEvent(
                audit.getCommandId().toString(),
                audit.getSiteId(),
                audit.getLineId(),
                audit.getTagCode(),
                status.asString(),
                null,
                ex.getMessage(),
                now));
        throw new CommandException(ex.getCode(), ex.getMessage(), ex.getStatus(), commandId, ex.getFieldErrors());
    }

    private static String stringifyTarget(CommandRequest request) {
        if (request.targetValue() == null || request.targetValue().isNull()) {
            return "null";
        }
        if (request.targetValue().isValueNode()) {
            return request.targetValue().asText();
        }
        return request.targetValue().toString();
    }
}
