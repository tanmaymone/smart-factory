package com.factory.central.stream;

import java.time.Instant;
import java.util.Set;

/**
 * SSE {@code COMMAND_UPDATE} payload. {@code status} is a String so M1.6 can map
 * {@code CommandStatus.name()} without changing the JSON contract.
 */
public record CommandUpdateEvent(
        String commandId,
        String siteId,
        String lineId,
        String tagCode,
        String status,
        Object appliedValue,
        String errorMessage,
        Instant executedAt) {

    /** Architecture {@code command_audit_logs.status} values — single source for M1.5/M1.6. */
    public static final class Status {
        public static final String VALIDATED_DISPATCHED = "VALIDATED_DISPATCHED";
        public static final String REJECTED_VALIDATION = "REJECTED_VALIDATION";
        public static final String REJECTED_INTERLOCK = "REJECTED_INTERLOCK";
        public static final String ACKED = "ACKED";
        public static final String FAILED_EDGE = "FAILED_EDGE";
        public static final String TIMEOUT = "TIMEOUT";

        public static final Set<String> ALL = Set.of(
                VALIDATED_DISPATCHED,
                REJECTED_VALIDATION,
                REJECTED_INTERLOCK,
                ACKED,
                FAILED_EDGE,
                TIMEOUT);

        private Status() {}
    }
}
