package com.factory.central.registry;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("SF-M1.3")
class TagMappingReconcileTest {

    private static final String NSU_1007 =
            "nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=1007";
    private static final String NSU_1002 =
            "nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=1002";

    @Test
    void retargetsLegacyGeneratorNodesOnly() {
        TagMapping valve = tag("VALVE_101_POS", "ns=3;i=1001");
        assertThat(valve.reconcileSeed(valve.getEndpointUrl(), null, null, null, null, NSU_1007))
                .isTrue();
        assertThat(valve.getExternalAddress()).isEqualTo(NSU_1007);

        TagMapping valveShared = tag("VALVE_101_POS", "ns=3;i=1007");
        assertThat(
                        valveShared.reconcileSeed(
                                valveShared.getEndpointUrl(), null, null, null, null, NSU_1007))
                .isTrue();
        assertThat(valveShared.getExternalAddress()).isEqualTo(NSU_1007);

        TagMapping custom = tag("VALVE_101_POS", "ns=3;s=Valve101Pos");
        assertThat(custom.reconcileSeed(custom.getEndpointUrl(), null, null, null, null, NSU_1007))
                .isFalse();
        assertThat(custom.getExternalAddress()).isEqualTo("ns=3;s=Valve101Pos");

        TagMapping temp = tag("REACTOR_TEMP_C", "ns=3;i=1002");
        assertThat(temp.reconcileSeed(temp.getEndpointUrl(), null, null, null, null, NSU_1002))
                .isTrue();
        assertThat(temp.getExternalAddress()).isEqualTo(NSU_1002);
    }

    private static TagMapping tag(String tagCode, String address) {
        return new TagMapping(
                UUID.randomUUID(),
                "INDORE",
                "LINE-01",
                tagCode,
                "desc",
                Protocol.OPC_UA,
                "opc.tcp://localhost:53530/OPCUA/SimulationServer",
                address,
                TagDataType.FLOAT,
                "%",
                true,
                0.0,
                100.0,
                0.5,
                null,
                null,
                null,
                null,
                null);
    }
}
