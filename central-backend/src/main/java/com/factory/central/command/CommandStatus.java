package com.factory.central.command;

import java.util.Set;

/** Persisted audit statuses — same strings as stream {@code COMMAND_UPDATE} payloads. */
public enum CommandStatus {
    VALIDATED_DISPATCHED,
    REJECTED_VALIDATION,
    REJECTED_INTERLOCK,
    ACKED,
    FAILED_EDGE,
    TIMEOUT;

    public static final Set<String> NAMES = Set.of(
            VALIDATED_DISPATCHED.name(),
            REJECTED_VALIDATION.name(),
            REJECTED_INTERLOCK.name(),
            ACKED.name(),
            FAILED_EDGE.name(),
            TIMEOUT.name());

    public String asString() {
        return name();
    }

    public static CommandStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("command status must not be blank");
        }
        return CommandStatus.valueOf(value.trim());
    }
}
