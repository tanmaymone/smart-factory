package com.factory.edge.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.factory.edge.mqtt.MqttProperties;
import com.factory.edge.opcua.MiloOpcUaSession;
import com.factory.edge.opcua.OpcUaProperties;
import com.factory.edge.opcua.ProbeTestServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@Testcontainers
@Tag("EDGE-CMD")
class MqttCommandAckIT {

    @Container
    static GenericContainer<?> mosquitto =
            new GenericContainer<>(DockerImageName.parse("eclipse-mosquitto:2"))
                    .withExposedPorts(1883)
                    .withCopyFileToContainer(
                            MountableFile.forClasspathResource("mosquitto-test.conf"),
                            "/mosquitto/config/mosquitto.conf");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void commandPublishYieldsAckAndHistory() throws Exception {
        try (ProbeTestServer server = new ProbeTestServer()) {
            String broker = "tcp://" + mosquitto.getHost() + ":" + mosquitto.getMappedPort(1883);
            OpcUaProperties opc = new OpcUaProperties();
            opc.setEndpointUrl(server.endpointUrl());
            opc.setRequestTimeoutMs(5000);
            opc.setPublishingIntervalMs(100);
            OpcUaProperties.TagConfig tag = new OpcUaProperties.TagConfig();
            tag.setSiteId("INDORE");
            tag.setLineId("LINE-01");
            tag.setTagCode("REACTOR_TEMP_SP");
            tag.setNodeId(server.doubleNodeIdText());
            tag.setDeadband(0.1);
            opc.setTags(List.of(tag));

            MiloOpcUaSession session = new MiloOpcUaSession(opc);
            EdgeCommandHistory history = new EdgeCommandHistory();
            MqttCommandLink link = null;
            MqttClient subscriber = null;
            MqttClient publisher = null;
            try {
                session.start(opc.getTags(), reading -> {}, generation -> {});
                OpcUaCommandWriter writer = new OpcUaCommandWriter(session);
                CommandDispatcher dispatcher =
                        new CommandDispatcher(opc, writer, history, objectMapper);

                MqttProperties mqtt = new MqttProperties();
                mqtt.setBrokerUrl(broker);
                mqtt.setClientId("edge-cmd-it-" + UUID.randomUUID());
                mqtt.setReconnectInitialMs(200);
                mqtt.setReconnectMaxMs(500);
                link = new MqttCommandLink(mqtt, dispatcher, history, objectMapper);

                CopyOnWriteArrayList<JsonNode> acks = new CopyOnWriteArrayList<>();
                subscriber = new MqttClient(broker, "ack-sub-" + UUID.randomUUID());
                MqttConnectOptions options = new MqttConnectOptions();
                options.setCleanSession(true);
                subscriber.connect(options);
                subscriber.subscribe(
                        "site/+/line/+/commands/acks",
                        (topic, message) ->
                                acks.add(
                                        objectMapper.readTree(
                                                new String(
                                                        message.getPayload(),
                                                        StandardCharsets.UTF_8))));

                String commandId = UUID.randomUUID().toString();
                ObjectNode body = objectMapper.createObjectNode();
                body.put("commandId", commandId);
                body.put("siteId", "INDORE");
                body.put("lineId", "LINE-01");
                body.put("tagCode", "REACTOR_TEMP_SP");
                body.put("protocol", "OPC_UA");
                body.put("externalAddress", server.doubleNodeIdText());
                body.put("dataType", "FLOAT");
                body.put("targetValue", 42.5);

                publisher = new MqttClient(broker, "cmd-pub-" + UUID.randomUUID());
                publisher.connect(options);
                MqttMessage message =
                        new MqttMessage(
                                objectMapper.writeValueAsString(body).getBytes(StandardCharsets.UTF_8));
                message.setQos(1);
                publisher.publish("site/INDORE/line/LINE-01/commands", message);

                await().atMost(Duration.ofSeconds(20))
                        .untilAsserted(
                                () ->
                                        assertThat(acks)
                                                .anySatisfy(
                                                        ack -> {
                                                            assertThat(ack.path("commandId").asText())
                                                                    .isEqualTo(commandId);
                                                            assertThat(ack.path("siteId").asText())
                                                                    .isEqualTo("INDORE");
                                                            assertThat(ack.path("lineId").asText())
                                                                    .isEqualTo("LINE-01");
                                                            assertThat(ack.path("status").asText())
                                                                    .isEqualTo("ACKED");
                                                            assertThat(ack.path("appliedValue").asDouble())
                                                                    .isEqualTo(42.5);
                                                            assertThat(ack.path("observedValue").asDouble())
                                                                    .isEqualTo(42.5);
                                                            assertThat(ack.get("errorMessage").isNull())
                                                                    .isTrue();
                                                            assertThat(ack.get("reason").isNull())
                                                                    .isTrue();
                                                            assertThat(ack.path("executedAt").asText())
                                                                    .isNotBlank();
                                                        }));

                assertThat(history.find(commandId)).isPresent();
                assertThat(history.find(commandId).get().result().status())
                        .isEqualTo(CommandResult.ACKED);
            } finally {
                closeQuiet(publisher);
                closeQuiet(subscriber);
                if (link != null) {
                    link.shutdown();
                }
                session.close();
            }
        }
    }

    private static void closeQuiet(MqttClient client) {
        if (client == null) {
            return;
        }
        try {
            if (client.isConnected()) {
                client.disconnect();
            }
            client.close();
        } catch (Exception ignored) {
            // best-effort test cleanup
        }
    }
}
