package com.factory.central.command;

import com.factory.central.stream.CommandUpdateEvent;
import java.util.Set;

/** Persisted audit statuses — same strings as {@link CommandUpdateEvent.Status}. */
public enum CommandStatus {
    VALIDATED_DISPATCHED,
    REJECTED_VALIDATION,
    REJECTED_INTERLOCK,
    ACKED,
    FAILED_EDGE,
    TIMEOUT;

    public static final Set<String> NAMES = CommandUpdateEvent.Status.ALL;

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
