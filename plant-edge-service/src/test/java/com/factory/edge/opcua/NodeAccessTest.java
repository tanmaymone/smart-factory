package com.factory.edge.opcua;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("EDGE-CMD")
class NodeAccessTest {

    @Test
    void currentWriteRequiresBothAccessLevels() {
        assertThat(NodeAccess.currentWrite(3, 3)).isTrue();
        assertThat(NodeAccess.currentWrite(1, 3)).isFalse();
        assertThat(NodeAccess.currentWrite(7, 1)).isFalse();
        assertThat(NodeAccess.currentWrite(Unsigned.ubyte(3), Unsigned.ubyte(3))).isTrue();
        assertThat(NodeAccess.currentWrite(null, 3)).isNull();
        assertThat(NodeAccess.currentWrite(3, "nope")).isNull();
    }
}
