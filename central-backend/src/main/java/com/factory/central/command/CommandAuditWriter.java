package com.factory.central.command;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommandAuditWriter {

    private final CommandAuditLogRepository repository;

    public CommandAuditWriter(CommandAuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public CommandAuditLog insert(CommandAuditLog log) {
        return repository.save(log);
    }

    @Transactional
    public int transitionFromDispatched(
            UUID commandId,
            CommandStatus status,
            String appliedValue,
            String rejectionReason,
            Instant ackedAt) {
        return repository.transitionFromDispatched(
                commandId, status.asString(), appliedValue, rejectionReason, ackedAt);
    }

    @Transactional(readOnly = true)
    public CommandAuditLog require(UUID commandId) {
        return repository
                .findByCommandId(commandId)
                .orElseThrow(() -> new IllegalStateException("command not found: " + commandId));
    }
}
