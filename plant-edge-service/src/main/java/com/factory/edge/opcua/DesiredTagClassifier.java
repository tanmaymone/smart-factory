package com.factory.edge.opcua;

import com.factory.edge.filter.DeadbandFilter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Config-level desired-tag validation. No OPC UA I/O, so it runs before any connect attempt. */
public final class DesiredTagClassifier {

    private DesiredTagClassifier() {}

    /** Splits the central manifest into dial-able candidates and config-level rejects. */
    public static OpcUaApply.Result classify(List<OpcUaProperties.TagConfig> desired) {
        if (desired == null || desired.isEmpty()) {
            return OpcUaApply.Result.empty();
        }
        List<OpcUaProperties.TagConfig> candidates = new ArrayList<>(desired.size());
        List<OpcUaApply.Reject> rejected = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (OpcUaProperties.TagConfig tag : desired) {
            String reason = reasonToReject(tag, seen);
            if (reason == null) {
                candidates.add(tag);
            } else {
                rejected.add(OpcUaApply.Reject.of(tag, reason));
            }
        }
        return new OpcUaApply.Result(List.copyOf(candidates), List.copyOf(rejected));
    }

    public static String keyOf(OpcUaProperties.TagConfig tag) {
        return DeadbandFilter.key(tag.getSiteId(), tag.getLineId(), tag.getTagCode());
    }

    private static String reasonToReject(OpcUaProperties.TagConfig tag, Set<String> seen) {
        if (blank(tag.getSiteId()) || blank(tag.getLineId()) || blank(tag.getTagCode())) {
            return OpcUaApply.INVALID_IDENTITY;
        }
        String protocol = tag.getProtocol();
        if (!blank(protocol) && !"OPC_UA".equalsIgnoreCase(protocol.trim())) {
            return OpcUaApply.UNSUPPORTED_PROTOCOL;
        }
        if (tag.getDeadband() < 0.0) {
            return OpcUaApply.INVALID_DEADBAND;
        }
        try {
            NodeIdParser.parse(tag.getNodeId());
        } catch (RuntimeException e) {
            return OpcUaApply.INVALID_NODE_ID;
        }
        if (!seen.add(keyOf(tag))) {
            return OpcUaApply.DUPLICATE_TAG;
        }
        return null;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
