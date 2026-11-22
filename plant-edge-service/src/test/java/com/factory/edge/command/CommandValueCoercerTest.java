package com.factory.edge.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.factory.edge.command.CommandValueCoercer.CoerceFail;
import com.factory.edge.command.CommandValueCoercer.CoerceOk;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("EDGE-CMD")
class CommandValueCoercerTest {

    @Test
    void floatEncodesExactFloatOrDoubleWidth() {
        Object floatOk = CommandValueCoercer.coerce("FLOAT", new DoubleNode(50.5), NodeIds.Float);
        assertThat(floatOk).isInstanceOf(CoerceOk.class);
        assertThat(((CoerceOk) floatOk).variant().getValue()).isInstanceOf(Float.class);

        Object doubleOk = CommandValueCoercer.coerce("FLOAT", new DoubleNode(50.5), NodeIds.Double);
        assertThat(doubleOk).isInstanceOf(CoerceOk.class);
        assertThat(((CoerceOk) doubleOk).variant().getValue()).isInstanceOf(Double.class);

        Object wrongWidth = CommandValueCoercer.coerce("FLOAT", new DoubleNode(50.5), NodeIds.Int32);
        assertThat(wrongWidth).isInstanceOf(CoerceFail.class);
    }

    @Test
    void rejectsNumericStringBooleanZeroOneFractionalIntAndLocalizedText() {
        assertThat(CommandValueCoercer.coerce("FLOAT", new TextNode("50.0"), NodeIds.Double))
                .isInstanceOf(CoerceFail.class);
        assertThat(CommandValueCoercer.coerce("FLOAT", BooleanNode.TRUE, NodeIds.Double))
                .isInstanceOf(CoerceFail.class);
        assertThat(CommandValueCoercer.coerce("BOOLEAN", new IntNode(0), NodeIds.Boolean))
                .isInstanceOf(CoerceFail.class);
        assertThat(CommandValueCoercer.coerce("BOOLEAN", new IntNode(1), NodeIds.Boolean))
                .isInstanceOf(CoerceFail.class);
        assertThat(CommandValueCoercer.coerce("INT", new DoubleNode(50.5), NodeIds.Int32))
                .isInstanceOf(CoerceFail.class);
        assertThat(CommandValueCoercer.coerce("INT", new DoubleNode(50.0), NodeIds.Int32))
                .isInstanceOf(CoerceOk.class);
        assertThat(CommandValueCoercer.coerce("STRING", new TextNode("ok"), NodeIds.LocalizedText))
                .isInstanceOf(CoerceFail.class);
        assertThat(CommandValueCoercer.coerce("FLOAT", new DoubleNode(1.0), NodeIds.Float))
                .isInstanceOf(CoerceOk.class);
    }
}
