package com.factory.edge.opcua;

import java.util.List;

/** Lenient-apply vocabulary shared by {@link OpcUaSession} and the config reconciler. */
public final class OpcUaApply {

    public static final String UNSUPPORTED_PROTOCOL = "unsupported protocol";
    public static final String INVALID_IDENTITY = "missing siteId/lineId/tagCode";
    public static final String INVALID_DEADBAND = "deadband must be >= 0";
    public static final String DUPLICATE_TAG = "duplicate site/line/tagCode";
    public static final String INVALID_NODE_ID = "invalid node id syntax";
    public static final String UNKNOWN_NAMESPACE = "unknown namespace uri";
    public static final String BAD_INITIAL_READ = "bad initial read";
    public static final String BAD_MONITORED_ITEM = "bad monitored item create";

    private OpcUaApply() {}

    public record Reject(String lineId, String tagCode, String externalAddress, String reason) {

        static Reject of(OpcUaProperties.TagConfig tag, String reason) {
            return new Reject(tag.getLineId(), tag.getTagCode(), tag.getNodeId(), reason);
        }
    }

    /** Accepted (subscribed) tags plus every tag dropped with its reason. */
    public record Result(List<OpcUaProperties.TagConfig> accepted, List<Reject> rejected) {

        public static Result empty() {
            return new Result(List.of(), List.of());
        }
    }
}
