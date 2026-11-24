package com.factory.edge.opcua;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadResponse;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** One-shot AccessLevel / UserAccessLevel read. Failure leaves writability unknown. */
final class NodeAccessProbe {

    private static final Logger log = LoggerFactory.getLogger(NodeAccessProbe.class);

    private NodeAccessProbe() {}

    static Map<NodeId, Boolean> readWritable(OpcUaClient client, List<NodeId> nodeIds) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            return Map.of();
        }
        try {
            ReadValueId[] ids = new ReadValueId[nodeIds.size() * 2];
            for (int i = 0; i < nodeIds.size(); i++) {
                ids[i * 2] = readId(nodeIds.get(i), AttributeId.AccessLevel);
                ids[i * 2 + 1] = readId(nodeIds.get(i), AttributeId.UserAccessLevel);
            }
            ReadResponse response = client.read(0.0, TimestampsToReturn.Neither, List.of(ids));
            DataValue[] results = response.getResults();
            if (results == null || results.length < ids.length) {
                return Map.of();
            }
            Map<NodeId, Boolean> writable = new HashMap<>();
            for (int i = 0; i < nodeIds.size(); i++) {
                writable.put(
                        nodeIds.get(i),
                        NodeAccess.currentWrite(raw(results[i * 2]), raw(results[i * 2 + 1])));
            }
            return writable;
        } catch (Exception e) {
            log.warn("OPC UA access-level read failed: {}", e.toString());
            return Map.of();
        }
    }

    private static ReadValueId readId(NodeId nodeId, AttributeId attributeId) {
        return new ReadValueId(nodeId, attributeId.uid(), null, QualifiedName.NULL_VALUE);
    }

    private static Object raw(DataValue value) {
        if (value == null) {
            return null;
        }
        StatusCode status = value.getStatusCode();
        if (status != null && status.isBad()) {
            return null;
        }
        Variant variant = value.getValue();
        if (variant == null || variant.isNull()) {
            return null;
        }
        return variant.getValue();
    }
}
