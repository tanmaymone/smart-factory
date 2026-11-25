package com.factory.edge.opcua;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("EDGE-CONFIG")
class DesiredTagClassifierTest {

    private static final String NSU = "nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=1007";

    @Test
    void emptyDesiredYieldsEmptyResult() {
        assertThat(DesiredTagClassifier.classify(null).accepted()).isEmpty();
        assertThat(DesiredTagClassifier.classify(List.of()).rejected()).isEmpty();
    }

    @Test
    void goodTagIsAcceptedAndBlankProtocolDefaultsToOpcUa() {
        OpcUaProperties.TagConfig blankProtocol = tag("LINE-01", "VALVE_101_POS", NSU, 0.5);
        blankProtocol.setProtocol("");

        OpcUaApply.Result result =
                DesiredTagClassifier.classify(
                        List.of(tag("LINE-01", "REACTOR_TEMP_C", NSU, 0.2), blankProtocol));

        assertThat(result.accepted()).hasSize(2);
        assertThat(result.rejected()).isEmpty();
    }

    @Test
    void nonOpcUaProtocolIsRejectedWithoutTouchingTheServer() {
        OpcUaProperties.TagConfig modbus = tag("LINE-01", "FLOW_01", "DB1.DBD0", 0.0);
        modbus.setProtocol("MODBUS_TCP");

        OpcUaApply.Result result = DesiredTagClassifier.classify(List.of(modbus));

        assertThat(result.accepted()).isEmpty();
        assertThat(result.rejected())
                .singleElement()
                .satisfies(
                        reject -> {
                            assertThat(reject.tagCode()).isEqualTo("FLOW_01");
                            assertThat(reject.reason()).isEqualTo(OpcUaApply.UNSUPPORTED_PROTOCOL);
                        });
    }

    @Test
    void badSyntaxNegativeDeadbandAndDuplicatesAreRejectedIndividually() {
        OpcUaApply.Result result =
                DesiredTagClassifier.classify(
                        List.of(
                                tag("LINE-01", "VALVE_101_POS", NSU, 0.5),
                                tag("LINE-01", "VALVE_101_POS", NSU, 0.5),
                                tag("LINE-01", "BAD_SYNTAX", "ns=3;i=1007", 0.0),
                                tag("LINE-01", "BLANK_ADDRESS", "  ", 0.0),
                                tag("LINE-01", "NEG_BAND", NSU, -0.1),
                                tag("", "NO_LINE", NSU, 0.0)));

        assertThat(result.accepted()).singleElement().extracting(OpcUaProperties.TagConfig::getTagCode)
                .isEqualTo("VALVE_101_POS");
        assertThat(result.rejected())
                .extracting(OpcUaApply.Reject::tagCode, OpcUaApply.Reject::reason)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "VALVE_101_POS", OpcUaApply.DUPLICATE_TAG),
                        org.assertj.core.groups.Tuple.tuple("BAD_SYNTAX", OpcUaApply.INVALID_NODE_ID),
                        org.assertj.core.groups.Tuple.tuple(
                                "BLANK_ADDRESS", OpcUaApply.INVALID_NODE_ID),
                        org.assertj.core.groups.Tuple.tuple("NEG_BAND", OpcUaApply.INVALID_DEADBAND),
                        org.assertj.core.groups.Tuple.tuple("NO_LINE", OpcUaApply.INVALID_IDENTITY));
    }

    private static OpcUaProperties.TagConfig tag(
            String lineId, String tagCode, String address, double deadband) {
        OpcUaProperties.TagConfig config = new OpcUaProperties.TagConfig();
        config.setSiteId("INDORE");
        config.setLineId(lineId);
        config.setTagCode(tagCode);
        config.setNodeId(address);
        config.setDeadband(deadband);
        return config;
    }
}
