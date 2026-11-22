package com.factory.edge.opcua;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("EDGE-OPC-PROBE")
class NodeIdParserTest {

    @Test
    void parsesNumericNodeId() {
        NodeId nodeId = NodeIdParser.parse("ns=3;i=1001");
        assertThat(nodeId.getNamespaceIndex().intValue()).isEqualTo(3);
        assertThat(nodeId.getIdentifier().toString()).isEqualTo("1001");
    }

    @Test
    void parsesStringNodeId() {
        NodeId nodeId = NodeIdParser.parse("ns=2;s=Counter");
        assertThat(nodeId.getNamespaceIndex().intValue()).isEqualTo(2);
        assertThat(nodeId.getIdentifier()).isEqualTo("Counter");
    }

    @Test
    void rejectsGuidAndOpaqueAndMalformed() {
        assertThatThrownBy(() -> NodeIdParser.parse("ns=3;g=00000000-0000-0000-0000-000000000000"))
                .isInstanceOf(OpcUaProbeException.class)
                .hasMessageContaining("Invalid NodeId syntax");
        assertThatThrownBy(() -> NodeIdParser.parse("i=1001"))
                .isInstanceOf(OpcUaProbeException.class);
        assertThatThrownBy(() -> NodeIdParser.parse(" "))
                .isInstanceOf(OpcUaProbeException.class);
    }
}
