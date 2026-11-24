package com.factory.edge.opcua;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("EDGE-NS")
class NodeIdParserTest {

    @Test
    void parsesNsuNumericAndStringWithEmbeddedSemicolon() {
        NodeAddress numeric =
                NodeIdParser.parse("nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=1007");
        assertThat(numeric).isInstanceOf(NodeAddress.Unresolved.class);
        NodeAddress.Unresolved u = (NodeAddress.Unresolved) numeric;
        assertThat(u.namespaceUri()).isEqualTo("http://www.prosysopc.com/OPCUA/SimulationNodes/");
        assertThat(u.identifier()).isEqualTo(new NodeAddress.NumericId(1007));

        NodeAddress stringId = NodeIdParser.parse("nsu=urn:factory:edge:probe-test;s=Valve;101");
        assertThat(stringId).isInstanceOf(NodeAddress.Unresolved.class);
        NodeAddress.Unresolved s = (NodeAddress.Unresolved) stringId;
        assertThat(s.namespaceUri()).isEqualTo("urn:factory:edge:probe-test");
        assertThat(s.identifier()).isEqualTo(new NodeAddress.StringId("Valve;101"));
    }

    @Test
    void parsesNs0BuiltInAndRejectsNonZeroIndex() {
        NodeAddress resolved = NodeIdParser.parse("ns=0;i=2255");
        assertThat(resolved).isInstanceOf(NodeAddress.Resolved.class);
        NodeId nodeId = ((NodeAddress.Resolved) resolved).nodeId();
        assertThat(nodeId.getNamespaceIndex().intValue()).isEqualTo(0);
        assertThat(nodeId.getIdentifier().toString()).isEqualTo("2255");

        assertThatThrownBy(() -> NodeIdParser.parse("ns=3;i=1001"))
                .isInstanceOf(OpcUaProbeException.class)
                .hasMessageContaining("Non-zero ns= index");
        assertThatThrownBy(() -> NodeIdParser.parse("ns=2;s=Counter"))
                .isInstanceOf(OpcUaProbeException.class)
                .hasMessageContaining("Non-zero ns= index");
        assertThatThrownBy(() -> NodeIdParser.parse("i=1001"))
                .isInstanceOf(OpcUaProbeException.class);
        assertThatThrownBy(() -> NodeIdParser.parse(" "))
                .isInstanceOf(OpcUaProbeException.class);
    }
}
