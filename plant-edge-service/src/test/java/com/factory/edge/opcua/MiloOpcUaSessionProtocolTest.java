package com.factory.edge.opcua;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("EDGE-OPC-PROBE")
class MiloOpcUaSessionProtocolTest {

    @Test
    void initialReadThenDataChangeAgainstEmbeddedServer() throws Exception {
        try (ProbeTestServer server = new ProbeTestServer()) {
            OpcUaProperties properties = new OpcUaProperties();
            properties.setEndpointUrl(server.endpointUrl());
            properties.setRequestTimeoutMs(5000);
            properties.setPublishingIntervalMs(100);
            properties.setMonitoredItemQueueSize(10);

            OpcUaProperties.TagConfig tag = new OpcUaProperties.TagConfig();
            tag.setSiteId("INDORE");
            tag.setLineId("LINE-01");
            tag.setTagCode("VALVE_101_POS");
            tag.setNodeId(server.counterNodeIdText());
            properties.setTags(List.of(tag));

            List<TagReading> readings = new CopyOnWriteArrayList<>();
            MiloOpcUaSession session = new MiloOpcUaSession(properties);
            try {
                session.start(properties.getTags(), readings::add, generation -> {});

                await().atMost(Duration.ofSeconds(5))
                        .untilAsserted(
                                () ->
                                        assertThat(readings)
                                                .anyMatch(
                                                        r ->
                                                                TagReading.EVENT_READ.equals(r.event())
                                                                        && "VALVE_101_POS"
                                                                                .equals(r.tagCode())
                                                                        && "GOOD".equals(r.quality())));

                server.setCounter(42);

                await().atMost(Duration.ofSeconds(5))
                        .untilAsserted(
                                () ->
                                        assertThat(readings)
                                                .anyMatch(
                                                        r ->
                                                                TagReading.EVENT_CHANGE.equals(r.event())
                                                                        && Integer.valueOf(42)
                                                                                .equals(r.value())
                                                                        && "GOOD".equals(r.quality())));

                List<String> lines = new ArrayList<>();
                readings.forEach(r -> lines.add(TagReadingFormatter.format(r)));
                assertThat(lines)
                        .anyMatch(line -> line.startsWith("OPC event=READ "))
                        .anyMatch(line -> line.startsWith("OPC event=CHANGE ") && line.contains("value=42"));
            } finally {
                session.close();
            }
        }
    }
}
