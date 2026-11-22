package com.factory.edge.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.factory.edge.opcua.MiloOpcUaSession;
import com.factory.edge.opcua.OpcUaProperties;
import com.factory.edge.opcua.ProbeTestServer;
import com.fasterxml.jackson.databind.node.DoubleNode;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("EDGE-CMD")
class OpcUaCommandWriterTest {

    private static final Instant NOW = Instant.parse("2026-11-21T12:05:00Z");

    @Test
    void floatWriteToDoubleNodeAcksWithReadback() throws Exception {
        try (ProbeTestServer server = new ProbeTestServer()) {
            OpcUaProperties properties = properties(server, server.doubleNodeIdText());
            MiloOpcUaSession session = new MiloOpcUaSession(properties);
            try {
                session.start(properties.getTags(), reading -> {}, generation -> {});
                OpcUaCommandWriter writer = new OpcUaCommandWriter(session);

                CommandResult result =
                        writer.write(
                                "c-write",
                                "INDORE",
                                "LINE-01",
                                "REACTOR_TEMP_SP",
                                server.doubleNodeIdText(),
                                "FLOAT",
                                new DoubleNode(50.0),
                                NOW);

                assertThat(result.status()).isEqualTo(CommandResult.ACKED);
                assertThat(result.terminal()).isTrue();
                assertThat(((Number) result.observedValue()).doubleValue()).isEqualTo(50.0);
            } finally {
                session.close();
            }
        }
    }

    @Test
    void readOnlyNodeRejected() throws Exception {
        try (ProbeTestServer server = new ProbeTestServer()) {
            OpcUaProperties properties = properties(server, server.readOnlyNodeIdText());
            MiloOpcUaSession session = new MiloOpcUaSession(properties);
            try {
                session.start(properties.getTags(), reading -> {}, generation -> {});
                OpcUaCommandWriter writer = new OpcUaCommandWriter(session);

                CommandResult result =
                        writer.write(
                                "c-ro",
                                "INDORE",
                                "LINE-01",
                                "REACTOR_TEMP_SP",
                                server.readOnlyNodeIdText(),
                                "FLOAT",
                                new DoubleNode(50.0),
                                NOW);

                assertThat(result.status()).isEqualTo(CommandResult.REJECTED);
                assertThat(result.terminal()).isTrue();
                assertThat(result.reason()).matches(".*(NotWritable|UserAccessDenied).*");
            } finally {
                session.close();
            }
        }
    }

    @Test
    void floatOntoInt32NodeRejectedBeforeWrite() throws Exception {
        try (ProbeTestServer server = new ProbeTestServer()) {
            OpcUaProperties properties = properties(server, server.counterNodeIdText());
            MiloOpcUaSession session = new MiloOpcUaSession(properties);
            try {
                session.start(properties.getTags(), reading -> {}, generation -> {});
                OpcUaCommandWriter writer = new OpcUaCommandWriter(session);

                CommandResult result =
                        writer.write(
                                "c-int",
                                "INDORE",
                                "LINE-01",
                                "VALVE_101_POS",
                                server.counterNodeIdText(),
                                "FLOAT",
                                new DoubleNode(50.0),
                                NOW);

                assertThat(result.status()).isEqualTo(CommandResult.REJECTED);
                assertThat(result.reason()).contains("Float or Double");
            } finally {
                session.close();
            }
        }
    }

    private static OpcUaProperties properties(ProbeTestServer server, String nodeId) {
        OpcUaProperties properties = new OpcUaProperties();
        properties.setEndpointUrl(server.endpointUrl());
        properties.setRequestTimeoutMs(5000);
        properties.setPublishingIntervalMs(100);
        OpcUaProperties.TagConfig tag = new OpcUaProperties.TagConfig();
        tag.setSiteId("INDORE");
        tag.setLineId("LINE-01");
        tag.setTagCode("TAG");
        tag.setNodeId(nodeId);
        tag.setDeadband(0.0);
        properties.setTags(List.of(tag));
        return properties;
    }
}
