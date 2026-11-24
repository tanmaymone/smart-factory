package com.factory.edge.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.factory.edge.command.CommandValueCoercer.CoerceFail;
import com.factory.edge.command.CommandValueCoercer.CoerceOk;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("EDGE-TYPE")
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
    void floatOverflowRejectedAsNonFinite() {
        Object overflow = CommandValueCoercer.coerce("FLOAT", new DoubleNode(1e40), NodeIds.Float);
        assertThat(overflow).isInstanceOf(CoerceFail.class);
        assertThat(((CoerceFail) overflow).reason()).contains("overflows Float");
    }

    @Test
    void uint16GetsUShortAndRejectsOutOfRange() {
        Object ok = CommandValueCoercer.coerce("INT", new IntNode(65535), NodeIds.UInt16);
        assertThat(ok).isInstanceOf(CoerceOk.class);
        assertThat(((CoerceOk) ok).variant().getValue()).isInstanceOf(UShort.class);
        assertThat(((UShort) ((CoerceOk) ok).variant().getValue()).intValue()).isEqualTo(65535);

        Object zero = CommandValueCoercer.coerce("INT", new IntNode(0), NodeIds.UInt16);
        assertThat(zero).isInstanceOf(CoerceOk.class);
        assertThat(((CoerceOk) zero).variant().getValue()).isInstanceOf(UShort.class);

        assertThat(CommandValueCoercer.coerce("INT", new IntNode(-1), NodeIds.UInt16))
                .isInstanceOf(CoerceFail.class);
        assertThat(CommandValueCoercer.coerce("INT", new IntNode(65536), NodeIds.UInt16))
                .isInstanceOf(CoerceFail.class);
        assertThat(CommandValueCoercer.coerce("INT", new DoubleNode(10.0), NodeIds.UInt16))
                .isInstanceOf(CoerceOk.class);
        assertThat(((CoerceOk) CommandValueCoercer.coerce("INT", new DoubleNode(10.0), NodeIds.UInt16))
                        .variant()
                        .getValue())
                .isNotInstanceOf(Integer.class)
                .isNotInstanceOf(Double.class);
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
