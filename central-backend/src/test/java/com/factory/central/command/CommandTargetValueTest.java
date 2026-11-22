package com.factory.central.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.factory.central.registry.Protocol;
import com.factory.central.registry.TagDataType;
import com.factory.central.registry.TagMapping;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("SF-M1.6")
class CommandTargetValueTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void inclusiveBoundsPassAtEdges() throws Exception {
        TagMapping tag = floatTag(0.0, 100.0);
        assertThat(CommandTargetValue.parse(tag, mapper.readTree("0")).value()).isEqualTo(0.0);
        assertThat(CommandTargetValue.parse(tag, mapper.readTree("100")).value()).isEqualTo(100.0);
        CommandTargetValue.assertWithinBounds(tag, 0.0);
        CommandTargetValue.assertWithinBounds(tag, 100.0);
    }

    @Test
    void outOfRangeRejected() {
        TagMapping tag = floatTag(0.0, 100.0);
        assertThatThrownBy(() -> CommandTargetValue.assertWithinBounds(tag, -0.1))
                .isInstanceOf(CommandException.class)
                .extracting(ex -> ((CommandException) ex).getCode())
                .isEqualTo(CommandErrorCode.TARGET_OUT_OF_RANGE);
    }

    @Test
    void nonFiniteRejectedBeforeRangeCompare() {
        TagMapping tag = floatTag(0.0, 100.0);
        assertThatThrownBy(() -> CommandTargetValue.parse(tag, DoubleNode.valueOf(Double.NaN)))
                .isInstanceOf(CommandException.class)
                .extracting(ex -> ((CommandException) ex).getCode())
                .isEqualTo(CommandErrorCode.INVALID_VALUE);
        assertThatThrownBy(() -> CommandTargetValue.parse(tag, DoubleNode.valueOf(Double.POSITIVE_INFINITY)))
                .isInstanceOf(CommandException.class)
                .extracting(ex -> ((CommandException) ex).getCode())
                .isEqualTo(CommandErrorCode.INVALID_VALUE);
        assertThatThrownBy(() -> CommandTargetValue.parse(tag, new TextNode("NaN")))
                .isInstanceOf(CommandException.class)
                .extracting(ex -> ((CommandException) ex).getCode())
                .isEqualTo(CommandErrorCode.INVALID_VALUE);
    }

    @Test
    void misconfiguredBoundsRejected() {
        TagMapping tag = floatTag(100.0, 0.0);
        assertThatThrownBy(() -> CommandTargetValue.assertWithinBounds(tag, 50.0))
                .isInstanceOf(CommandException.class)
                .extracting(ex -> ((CommandException) ex).getCode())
                .isEqualTo(CommandErrorCode.TAG_BOUNDS_MISCONFIGURED);
    }

    private static TagMapping floatTag(Double min, Double max) {
        return new TagMapping(
                UUID.randomUUID(),
                "INDORE",
                "LINE-01",
                "VALVE_101_POS",
                "valve",
                Protocol.OPC_UA,
                "opc.tcp://localhost",
                "ns=3;i=1001",
                TagDataType.FLOAT,
                "%",
                true,
                min,
                max,
                0.5,
                null,
                null,
                null,
                null,
                null);
    }
}
