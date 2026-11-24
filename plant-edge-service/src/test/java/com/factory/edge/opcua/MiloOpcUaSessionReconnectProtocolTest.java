package com.factory.edge.opcua;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("EDGE-RECONNECT")
class MiloOpcUaSessionReconnectProtocolTest {

    @Test
    void closeThenStartRecreatesSubscriptionAndResolvesNsu() throws Exception {
        try (ProbeTestServer server = new ProbeTestServer()) {
            OpcUaProperties properties = properties(server);
            List<TagReading> readings = new CopyOnWriteArrayList<>();
            MiloOpcUaSession session = new MiloOpcUaSession(properties);

            try {
                session.start(properties.getTags(), readings::add, generation -> {});
                NodeId first = session.resolve(server.counterNodeIdText());
                assertThat(first.getNamespaceIndex().intValue()).isNotEqualTo(3);
                assertThat(first.getNamespaceIndex().intValue()).isGreaterThan(0);

                await().atMost(Duration.ofSeconds(5))
                        .untilAsserted(
                                () ->
                                        assertThat(readings)
                                                .anyMatch(
                                                        r ->
                                                                TagReading.EVENT_READ.equals(r.event())
                                                                        && "GOOD".equals(r.quality())));

                session.close();
                assertThat(session.isAvailable()).isFalse();
                readings.clear();

                session.start(properties.getTags(), readings::add, generation -> {});
                NodeId second = session.resolve(server.counterNodeIdText());
                assertThat(second).isEqualTo(first);

                server.setCounter(77);
                await().atMost(Duration.ofSeconds(5))
                        .untilAsserted(
                                () ->
                                        assertThat(readings)
                                                .anyMatch(
                                                        r ->
                                                                TagReading.EVENT_CHANGE.equals(r.event())
                                                                        && Integer.valueOf(77)
                                                                                .equals(r.value())));
            } finally {
                session.close();
            }
        }
    }

    @Test
    void failedStartClearsMapSoNextStartDoesNotUseStaleIndex() throws Exception {
        try (ProbeTestServer server = new ProbeTestServer()) {
            OpcUaProperties properties = properties(server);
            MiloOpcUaSession session = new MiloOpcUaSession(properties);
            AtomicInteger starts = new AtomicInteger();

            try {
                session.start(properties.getTags(), reading -> {}, generation -> {});
                assertThat(session.isAvailable()).isTrue();
                starts.incrementAndGet();

                OpcUaProperties.TagConfig bad = new OpcUaProperties.TagConfig();
                bad.setSiteId("INDORE");
                bad.setLineId("LINE-01");
                bad.setTagCode("MISSING");
                bad.setNodeId("nsu=urn:does:not:exist;i=1");
                bad.setDeadband(0.0);
                properties.setTags(List.of(bad));

                try {
                    session.start(properties.getTags(), reading -> {}, generation -> {});
                } catch (OpcUaProbeException expected) {
                    assertThat(expected.getMessage()).contains("Unknown namespace URI");
                }
                assertThat(session.isAvailable()).isFalse();

                properties.setTags(
                        List.of(
                                tag(
                                        "INDORE",
                                        "LINE-01",
                                        "VALVE_101_POS",
                                        server.counterNodeIdText())));
                session.start(properties.getTags(), reading -> {}, generation -> {});
                assertThat(session.isAvailable()).isTrue();
                assertThat(session.resolve(server.counterNodeIdText()).getNamespaceIndex().intValue())
                        .isGreaterThan(0);
                starts.incrementAndGet();
                assertThat(starts.get()).isEqualTo(2);
            } finally {
                session.close();
            }
        }
    }

    private static OpcUaProperties properties(ProbeTestServer server) {
        OpcUaProperties properties = new OpcUaProperties();
        properties.setEndpointUrl(server.endpointUrl());
        properties.setRequestTimeoutMs(5000);
        properties.setPublishingIntervalMs(100);
        properties.setMonitoredItemQueueSize(10);
        properties.setTags(
                List.of(tag("INDORE", "LINE-01", "VALVE_101_POS", server.counterNodeIdText())));
        return properties;
    }

    private static OpcUaProperties.TagConfig tag(
            String siteId, String lineId, String tagCode, String nodeId) {
        OpcUaProperties.TagConfig tag = new OpcUaProperties.TagConfig();
        tag.setSiteId(siteId);
        tag.setLineId(lineId);
        tag.setTagCode(tagCode);
        tag.setNodeId(nodeId);
        tag.setDeadband(0.0);
        return tag;
    }
}
