package com.factory.central.command;

import java.time.Instant;
import java.util.UUID;

public record CommandAuditResponse(
        UUID commandId,
        String siteId,
        String lineId,
        String tagCode,
        String requestedValue,
        String previousValue,
        String requestedBy,
        String reason,
        String status,
        String rejectionReason,
        String appliedValue,
        Instant createdAt,
        Instant ackedAt) {

    static CommandAuditResponse from(CommandAuditLog log) {
        return new CommandAuditResponse(
                log.getCommandId(),
                log.getSiteId(),
                log.getLineId(),
                log.getTagCode(),
                log.getRequestedValue(),
                log.getPreviousValue(),
                log.getRequestedBy(),
                log.getReason(),
                log.getStatus().asString(),
                log.getRejectionReason(),
                log.getAppliedValue(),
                log.getCreatedAt(),
                log.getAckedAt());
    }
}
