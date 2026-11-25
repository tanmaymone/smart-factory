package com.factory.edge.opcua;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;

/**
 * Static mode (config sync off) still binds an explicit {@code app.opcua.tags} list. The list lives
 * in {@code application-static-tags.yml} since M3.4 moved the live source to the central manifest.
 */
@Tag("EDGE-OPC-PROBE")
@SpringBootTest(classes = com.factory.edge.PlantEdgeApplication.class)
@ActiveProfiles("static-tags")
@TestPropertySource(
        properties = {
            "app.opcua.endpoint-url=opc.tcp://127.0.0.1:1/disabled",
            "app.buffer.path=${java.io.tmpdir}/edge-seed-alignment-test.db",
            "app.mqtt.broker-url=tcp://127.0.0.1:1",
            "spring.main.lazy-initialization=true",
            "spring.main.web-application-type=none"
        })
class SeedAlignmentTest {

    @MockitoBean
    private OpcUaProbe probe;

    @Autowired
    private OpcUaProperties properties;

    @Autowired
    private com.factory.edge.config.EdgeConfigProperties edgeConfig;

    @Test
    void staticModeKeepsSyncOff() {
        assertThat(edgeConfig.isConfigSyncEnabled()).isFalse();
    }

    @Test
    void yamlTagsMatchRegistrySeedRunnerExactly() {
        List<OpcUaProperties.TagConfig> tags = properties.getTags();
        assertThat(tags).hasSize(7);
        String nsu1007 = "nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=1007";
        String nsu1002 = "nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=1002";
        assertThat(tuple(tags.get(0))).isEqualTo("INDORE|LINE-01|VALVE_101_POS|" + nsu1007 + "|0.5");
        assertThat(tuple(tags.get(1))).isEqualTo("INDORE|LINE-01|REACTOR_TEMP_C|" + nsu1002 + "|0.2");
        assertThat(tuple(tags.get(2))).isEqualTo("INDORE|LINE-01|REACTOR_TEMP_SP|" + nsu1007 + "|0.1");
        assertThat(tuple(tags.get(3))).isEqualTo("INDORE|LINE-02|VALVE_201_POS|" + nsu1007 + "|0.5");
        assertThat(tuple(tags.get(4))).isEqualTo("PUNE|LINE-01|VALVE_101_POS|" + nsu1007 + "|0.5");
        assertThat(tuple(tags.get(5))).isEqualTo("PUNE|LINE-01|REACTOR_TEMP_C|" + nsu1002 + "|0.2");
        assertThat(tuple(tags.get(6))).isEqualTo("PUNE|LINE-01|REACTOR_TEMP_SP|" + nsu1007 + "|0.1");
    }

    private static String tuple(OpcUaProperties.TagConfig tag) {
        return tag.getSiteId()
                + "|"
                + tag.getLineId()
                + "|"
                + tag.getTagCode()
                + "|"
                + tag.getNodeId()
                + "|"
                + tag.getDeadband();
    }
}
