package com.factory.edge.opcua;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("EDGE-OPC-PROBE")
class TagReadingFormatterTest {

    @Test
    void formatsReadAndChangeInStableFieldOrder() {
        OpcUaProperties.TagConfig tag = tag("INDORE", "LINE-01", "VALVE_101_POS", "ns=3;i=1001");
        Instant source = Instant.parse("2026-11-20T16:18:01.200Z");
        Instant server = Instant.parse("2026-11-20T16:18:01.205Z");
        DataValue value =
                new DataValue(
                        new Variant(1),
                        StatusCode.GOOD,
                        new DateTime(source),
                        new DateTime(server));

        String read = TagReadingFormatter.format(TagReading.from(TagReading.EVENT_READ, tag, value));
        assertThat(read)
                .isEqualTo(
                        "OPC event=READ siteId=INDORE lineId=LINE-01 tagCode=VALVE_101_POS"
                                + " nodeId=ns=3;i=1001 value=1 quality=GOOD"
                                + " sourceTimestamp=2026-11-20T16:18:01.200Z"
                                + " serverTimestamp=2026-11-20T16:18:01.205Z");

        String change =
                TagReadingFormatter.format(TagReading.from(TagReading.EVENT_CHANGE, tag, value));
        assertThat(change).startsWith("OPC event=CHANGE ");
    }

    @Test
    void formatsNullValueAndBadStatusName() {
        OpcUaProperties.TagConfig tag = tag("PUNE", "LINE-01", "REACTOR_TEMP_C", "ns=3;i=1002");
        DataValue value =
                new DataValue(
                        Variant.NULL_VALUE,
                        new StatusCode(StatusCodes.Bad_NodeIdUnknown),
                        null,
                        null);

        String line = TagReadingFormatter.format(TagReading.from(TagReading.EVENT_READ, tag, value));
        assertThat(line)
                .contains("value=null")
                .contains("quality=BAD")
                .contains("sourceTimestamp=null")
                .contains("serverTimestamp=null")
                .contains("status=Bad_NodeIdUnknown");
    }

    private static OpcUaProperties.TagConfig tag(
            String siteId, String lineId, String tagCode, String nodeId) {
        OpcUaProperties.TagConfig config = new OpcUaProperties.TagConfig();
        config.setSiteId(siteId);
        config.setLineId(lineId);
        config.setTagCode(tagCode);
        config.setNodeId(nodeId);
        return config;
    }
}
