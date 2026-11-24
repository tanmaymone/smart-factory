package com.factory.edge.opcua;

import java.util.UUID;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;

/**
 * Plant NodeId before server-index resolution. {@code ns=0} is already a NodeId; {@code nsu=} waits
 * for {@link MiloOpcUaSession#resolve(String)}.
 */
public sealed interface NodeAddress permits NodeAddress.Resolved, NodeAddress.Unresolved {

    record Resolved(NodeId nodeId) implements NodeAddress {}

    record Unresolved(String namespaceUri, Identifier identifier) implements NodeAddress {}

    sealed interface Identifier permits NumericId, StringId, GuidId, OpaqueId {
        NodeId toNodeId(UShort namespaceIndex);
    }

    record NumericId(int value) implements Identifier {
        @Override
        public NodeId toNodeId(UShort namespaceIndex) {
            return new NodeId(namespaceIndex, value);
        }
    }

    record StringId(String value) implements Identifier {
        @Override
        public NodeId toNodeId(UShort namespaceIndex) {
            return new NodeId(namespaceIndex, value);
        }
    }

    record GuidId(UUID value) implements Identifier {
        @Override
        public NodeId toNodeId(UShort namespaceIndex) {
            return new NodeId(namespaceIndex, value);
        }
    }

    record OpaqueId(ByteString value) implements Identifier {
        @Override
        public NodeId toNodeId(UShort namespaceIndex) {
            return new NodeId(namespaceIndex, value);
        }
    }

    default NodeId toNodeId(UShort namespaceIndex) {
        return switch (this) {
            case Resolved r -> r.nodeId();
            case Unresolved u -> u.identifier().toNodeId(namespaceIndex);
        };
    }
}
