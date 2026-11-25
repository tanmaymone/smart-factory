package com.factory.edge.opcua;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;

/** Server_NamespaceArray read plus {@code nsu=} URI to namespace-index NodeId resolution. */
final class NamespaceResolver {

    private NamespaceResolver() {}

    record Lenient(List<ParsedTag> accepted, List<OpcUaApply.Reject> rejected) {}

    static Map<String, UShort> readTable(OpcUaClient client) {
        try {
            DataValue value =
                    client.readValue(0.0, TimestampsToReturn.Both, NodeIds.Server_NamespaceArray);
            if (!QualityGate.requireGood(value)) {
                StatusCode status = value == null ? null : value.getStatusCode();
                throw new OpcUaProbeException(
                        "Server_NamespaceArray quality is not GOOD status="
                                + (status == null ? "null" : TagReading.symbolicName(status)));
            }
            Variant variant = value.getValue();
            if (variant == null || variant.isNull() || !(variant.getValue() instanceof String[] uris)) {
                throw new OpcUaProbeException("Server_NamespaceArray value is missing or not String[]");
            }
            Map<String, UShort> map = new HashMap<>();
            for (int i = 0; i < uris.length; i++) {
                if (uris[i] != null && !uris[i].isBlank()) {
                    map.put(uris[i], UShort.valueOf(i));
                }
            }
            if (map.isEmpty()) {
                throw new OpcUaProbeException("Server_NamespaceArray is empty");
            }
            return Map.copyOf(map);
        } catch (OpcUaProbeException e) {
            throw e;
        } catch (Exception e) {
            throw new OpcUaProbeException(
                    "Failed to read Server_NamespaceArray: " + e.getMessage(), e);
        }
    }

    static NodeId toNodeId(Map<String, UShort> table, NodeAddress address) {
        return switch (address) {
            case NodeAddress.Resolved r -> r.nodeId();
            case NodeAddress.Unresolved u -> {
                UShort index = table.get(u.namespaceUri());
                if (index == null) {
                    throw new OpcUaProbeException(
                            "Unknown namespace URI (not in Server_NamespaceArray): "
                                    + u.namespaceUri());
                }
                yield u.identifier().toNodeId(index);
            }
        };
    }

    /** Strict mode: one invalid address fails the whole start before any connect. */
    static List<NodeAddress> parseAll(List<OpcUaProperties.TagConfig> tags) {
        List<NodeAddress> addresses = new ArrayList<>(tags.size());
        for (OpcUaProperties.TagConfig tag : tags) {
            try {
                addresses.add(NodeIdParser.parse(tag.getNodeId()));
            } catch (OpcUaProbeException e) {
                throw describe(tag, e);
            }
        }
        return addresses;
    }

    static List<ParsedTag> resolveAll(
            Map<String, UShort> table,
            List<OpcUaProperties.TagConfig> tags,
            List<NodeAddress> addresses) {
        List<ParsedTag> parsed = new ArrayList<>(tags.size());
        for (int i = 0; i < tags.size(); i++) {
            OpcUaProperties.TagConfig tag = tags.get(i);
            try {
                parsed.add(new ParsedTag(tag, toNodeId(table, addresses.get(i))));
            } catch (OpcUaProbeException e) {
                throw describe(tag, e);
            }
        }
        return parsed;
    }

    /** Hot-reload mode: a bad syntax or unknown namespace drops only that tag. */
    static Lenient resolveLenient(
            Map<String, UShort> table, List<OpcUaProperties.TagConfig> tags) {
        List<ParsedTag> accepted = new ArrayList<>(tags.size());
        List<OpcUaApply.Reject> rejected = new ArrayList<>();
        for (OpcUaProperties.TagConfig tag : tags) {
            try {
                accepted.add(new ParsedTag(tag, toNodeId(table, NodeIdParser.parse(tag.getNodeId()))));
            } catch (OpcUaProbeException e) {
                String reason =
                        e.getMessage() != null && e.getMessage().startsWith("Unknown namespace URI")
                                ? OpcUaApply.UNKNOWN_NAMESPACE
                                : OpcUaApply.INVALID_NODE_ID;
                rejected.add(OpcUaApply.Reject.of(tag, reason));
            }
        }
        return new Lenient(accepted, rejected);
    }

    private static OpcUaProbeException describe(
            OpcUaProperties.TagConfig tag, OpcUaProbeException cause) {
        return new OpcUaProbeException(
                "Invalid NodeId for siteId="
                        + tag.getSiteId()
                        + " lineId="
                        + tag.getLineId()
                        + " tagCode="
                        + tag.getTagCode()
                        + " nodeId="
                        + tag.getNodeId()
                        + ": "
                        + cause.getMessage(),
                cause);
    }
}
