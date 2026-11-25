package com.factory.edge.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.factory.edge.config.EdgeConfigProperties;
import com.factory.edge.opcua.OpcUaProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("EDGE-CMD")
class CommandDispatcherTest {

    private static final Instant NOW = Instant.parse("2026-11-21T12:00:00Z");

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<String> published = new ArrayList<>();
    private CommandWriter writer;
    private EdgeCommandHistory history;
    private CommandDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        OpcUaProperties properties = new OpcUaProperties();
        OpcUaProperties.TagConfig tag = new OpcUaProperties.TagConfig();
        tag.setSiteId("INDORE");
        tag.setLineId("LINE-01");
        tag.setTagCode("VALVE_101_POS");
        tag.setNodeId("nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=1001");
        properties.setTags(List.of(tag));

        writer = mock(CommandWriter.class);
        history = new EdgeCommandHistory();
        dispatcher =
                new CommandDispatcher(
                        properties,
                        new EdgeConfigProperties(),
                        writer,
                        history,
                        mapper,
                        Clock.fixed(NOW, ZoneOffset.UTC));
        dispatcher.bindAckSink(
                (topic, payload) -> {
                    published.add(topic + "|" + payload);
                    return true;
                });
    }

    @Test
    void unknownTagAndAddressMismatchAndUnsupportedProtocolReject() throws Exception {
        when(writer.write(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new AssertionError("should not write"));

        dispatcher.handle(topic(), bytes(command("u1", "MISSING", "nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=1001", "OPC_UA")));
        dispatcher.handle(topic(), bytes(command("u2", "VALVE_101_POS", "nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=9999", "OPC_UA")));
        dispatcher.handle(topic(), bytes(command("u3", "VALVE_101_POS", "nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=1001", "S7")));

        verify(writer, never()).write(any(), any(), any(), any(), any(), any(), any(), any());
        assertThat(published).hasSize(3);
        assertThat(published.get(0)).contains("\"status\":\"REJECTED\"").contains("unknown tag");
        assertThat(published.get(1)).contains("address does not match");
        assertThat(published.get(2)).contains("unsupported protocol");
    }

    @Test
    void unconfiguredSiteIgnoredAndTopicPayloadMismatchDroppedAndMissingCommandIdDropped()
            throws Exception {
        dispatcher.handle(
                "site/UNKNOWN/line/LINE-01/commands",
                bytes(command("c1", "VALVE_101_POS", "nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=1001", "OPC_UA")));
        ObjectNode mismatch = command("c1", "VALVE_101_POS", "nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=1001", "OPC_UA");
        mismatch.put("siteId", "PUNE");
        dispatcher.handle(topic(), bytes(mismatch));
        ObjectNode noId = command("c1", "VALVE_101_POS", "nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=1001", "OPC_UA");
        noId.remove("commandId");
        dispatcher.handle(topic(), bytes(noId));

        verify(writer, never()).write(any(), any(), any(), any(), any(), any(), any(), any());
        assertThat(published).isEmpty();
    }

    @Test
    void terminalDuplicateRepublishesWithoutRewriteTransientAllowsRetry() throws Exception {
        AtomicInteger writes = new AtomicInteger();
        when(writer.write(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(
                        inv -> {
                            writes.incrementAndGet();
                            String id = inv.getArgument(0);
                            if (writes.get() == 1) {
                                return CommandResult.rejected(
                                        id,
                                        "INDORE",
                                        "LINE-01",
                                        "VALVE_101_POS",
                                        "nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=1001",
                                        50.0,
                                        "OPC UA session unavailable",
                                        NOW,
                                        false);
                            }
                            return CommandResult.acked(
                                    id,
                                    "INDORE",
                                    "LINE-01",
                                    "VALVE_101_POS",
                                    "nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=1001",
                                    50.0,
                                    50.0,
                                    NOW);
                        });

        byte[] payload = bytes(command("c1", "VALVE_101_POS", "nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=1001", "OPC_UA"));
        dispatcher.handle(topic(), payload);
        assertThat(writes.get()).isEqualTo(1);
        assertThat(history.find("c1")).isEmpty();

        dispatcher.handle(topic(), payload);
        assertThat(writes.get()).isEqualTo(2);
        assertThat(history.find("c1")).isPresent();

        int publishedBefore = published.size();
        dispatcher.handle(topic(), payload);
        assertThat(writes.get()).isEqualTo(2);
        assertThat(published).hasSize(publishedBefore + 1);
    }

    @Test
    void successfulWritePublishesAckWithAliases() throws Exception {
        when(writer.write(
                        eq("c1"),
                        eq("INDORE"),
                        eq("LINE-01"),
                        eq("VALVE_101_POS"),
                        eq("nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=1001"),
                        eq("FLOAT"),
                        any(JsonNode.class),
                        eq(NOW)))
                .thenReturn(
                        CommandResult.acked(
                                "c1",
                                "INDORE",
                                "LINE-01",
                                "VALVE_101_POS",
                                "nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=1001",
                                50.0,
                                50.0,
                                NOW));

        dispatcher.handle(topic(), bytes(command("c1", "VALVE_101_POS", "nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=1001", "OPC_UA")));

        verify(writer, times(1)).write(any(), any(), any(), any(), any(), any(), any(), any());
        assertThat(published.get(0))
                .contains("site/INDORE/line/LINE-01/commands/acks")
                .contains("\"appliedValue\":50.0")
                .contains("\"observedValue\":50.0")
                .contains("\"status\":\"ACKED\"");
        assertThat(history.find("c1")).isPresent();
    }

    @Test
    void readOnlyTagIsRejectedBeforeAnyWrite() throws Exception {
        OpcUaProperties.TagConfig readOnly = new OpcUaProperties.TagConfig();
        readOnly.setSiteId("INDORE");
        readOnly.setLineId("LINE-01");
        readOnly.setTagCode("REACTOR_TEMP_C");
        readOnly.setNodeId("nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=1002");
        readOnly.setWritable(false);
        dispatcher.replaceConfiguredTags(List.of(readOnly));

        dispatcher.handle(
                topic(),
                bytes(
                        command(
                                "ro1",
                                "REACTOR_TEMP_C",
                                "nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=1002",
                                "OPC_UA")));

        verify(writer, never()).write(any(), any(), any(), any(), any(), any(), any(), any());
        assertThat(published).hasSize(1);
        assertThat(published.get(0))
                .contains("\"status\":\"REJECTED\"")
                .contains("tag is not writable");
    }

    @Test
    void replacedTagListTakesEffectForTheNextCommand() throws Exception {
        byte[] payload =
                bytes(
                        command(
                                "gone1",
                                "VALVE_101_POS",
                                "nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=1001",
                                "OPC_UA"));

        dispatcher.replaceConfiguredTags(List.of());
        dispatcher.handle(topic(), payload);

        // The line itself is no longer configured, so the message is not ours to ACK.
        verify(writer, never()).write(any(), any(), any(), any(), any(), any(), any(), any());
        assertThat(published).isEmpty();
    }

    @Test
    void syncModeDropsCrossSiteCommandsWithoutAck() throws Exception {
        EdgeConfigProperties edgeConfig = new EdgeConfigProperties();
        edgeConfig.setConfigSyncEnabled(true);
        edgeConfig.setSiteId("INDORE");

        OpcUaProperties.TagConfig pune = new OpcUaProperties.TagConfig();
        pune.setSiteId("PUNE");
        pune.setLineId("LINE-01");
        pune.setTagCode("VALVE_101_POS");
        pune.setNodeId("nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=1001");

        CommandDispatcher scoped =
                new CommandDispatcher(
                        new OpcUaProperties(),
                        edgeConfig,
                        writer,
                        history,
                        mapper,
                        Clock.fixed(NOW, ZoneOffset.UTC));
        scoped.bindAckSink(
                (topic, payload) -> {
                    published.add(topic + "|" + payload);
                    return true;
                });
        scoped.replaceConfiguredTags(List.of(pune));

        ObjectNode crossSite =
                command(
                        "x1",
                        "VALVE_101_POS",
                        "nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=1001",
                        "OPC_UA");
        crossSite.put("siteId", "PUNE");
        scoped.handle("site/PUNE/line/LINE-01/commands", bytes(crossSite));

        verify(writer, never()).write(any(), any(), any(), any(), any(), any(), any(), any());
        assertThat(published).isEmpty();
        assertThat(history.find("x1")).isEmpty();
    }

    private static String topic() {
        return "site/INDORE/line/LINE-01/commands";
    }

    private ObjectNode command(String commandId, String tagCode, String address, String protocol)
            throws Exception {
        ObjectNode node = mapper.createObjectNode();
        node.put("commandId", commandId);
        node.put("siteId", "INDORE");
        node.put("lineId", "LINE-01");
        node.put("tagCode", tagCode);
        node.put("protocol", protocol);
        node.put("externalAddress", address);
        node.put("dataType", "FLOAT");
        node.put("targetValue", 50.0);
        return node;
    }

    private byte[] bytes(ObjectNode node) throws Exception {
        return mapper.writeValueAsBytes(node);
    }
}
