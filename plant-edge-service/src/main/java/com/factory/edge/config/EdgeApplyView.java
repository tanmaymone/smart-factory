package com.factory.edge.config;

import java.time.Instant;
import java.util.List;

/** Immutable apply-owned status snapshot written only on opcua-reconnect. */
public record EdgeApplyView(
        Long appliedVersion,
        String appliedContentHash,
        Instant lastAppliedAt,
        List<RejectedTag> rejected,
        int rejectedCount,
        int activeTagCount) {

    public static EdgeApplyView empty() {
        return new EdgeApplyView(null, null, null, List.of(), 0, 0);
    }

    public record RejectedTag(String lineId, String tagCode, String externalAddress, String reason) {}
}
