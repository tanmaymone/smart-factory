package com.factory.edge.command;

import java.time.Instant;

/** Outcome of a single edge command attempt. */
public record CommandResult(
        String commandId,
        String siteId,
        String lineId,
        String tagCode,
        String nodeId,
        Object targetValue,
        String status,
        Object observedValue,
        String reason,
        Instant executedAt,
        boolean terminal) {

    public static final String ACKED = "ACKED";
    public static final String REJECTED = "REJECTED";

    public static CommandResult acked(
            String commandId,
            String siteId,
            String lineId,
            String tagCode,
            String nodeId,
            Object targetValue,
            Object observedValue,
            Instant executedAt) {
        return new CommandResult(
                commandId,
                siteId,
                lineId,
                tagCode,
                nodeId,
                targetValue,
                ACKED,
                observedValue,
                null,
                executedAt,
                true);
    }

    public static CommandResult rejected(
            String commandId,
            String siteId,
            String lineId,
            String tagCode,
            String nodeId,
            Object targetValue,
            String reason,
            Instant executedAt,
            boolean terminal) {
        return new CommandResult(
                commandId,
                siteId,
                lineId,
                tagCode,
                nodeId,
                targetValue,
                REJECTED,
                null,
                reason,
                executedAt,
                terminal);
    }
}
