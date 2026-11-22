package com.factory.central.command;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "command_audit_logs")
public class CommandAuditLog {

    @Id
    @Column(name = "command_id", nullable = false)
    private UUID commandId;

    @Column(name = "site_id", nullable = false, length = 64)
    private String siteId;

    @Column(name = "line_id", nullable = false, length = 64)
    private String lineId;

    @Column(name = "tag_code", nullable = false, length = 128)
    private String tagCode;

    @Column(name = "requested_value", nullable = false, length = 255)
    private String requestedValue;

    @Column(name = "previous_value", length = 255)
    private String previousValue;

    @Column(name = "requested_by", nullable = false, length = 255)
    private String requestedBy;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private CommandStatus status;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "applied_value", length = 255)
    private String appliedValue;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "acked_at")
    private Instant ackedAt;

    protected CommandAuditLog() {}

    public CommandAuditLog(
            UUID commandId,
            String siteId,
            String lineId,
            String tagCode,
            String requestedValue,
            String previousValue,
            String requestedBy,
            String reason,
            CommandStatus status,
            String rejectionReason,
            Instant createdAt) {
        this.commandId = commandId;
        this.siteId = siteId;
        this.lineId = lineId;
        this.tagCode = tagCode;
        this.requestedValue = requestedValue;
        this.previousValue = previousValue;
        this.requestedBy = requestedBy;
        this.reason = reason;
        this.status = status;
        this.rejectionReason = rejectionReason;
        this.createdAt = createdAt;
    }

    public UUID getCommandId() {
        return commandId;
    }

    public String getSiteId() {
        return siteId;
    }

    public String getLineId() {
        return lineId;
    }

    public String getTagCode() {
        return tagCode;
    }

    public String getRequestedValue() {
        return requestedValue;
    }

    public String getPreviousValue() {
        return previousValue;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public String getReason() {
        return reason;
    }

    public CommandStatus getStatus() {
        return status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public String getAppliedValue() {
        return appliedValue;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getAckedAt() {
        return ackedAt;
    }
}
