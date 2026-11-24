package com.factory.edge.mqtt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.factory.edge.buffer.BufferProperties;
import com.factory.edge.buffer.StoreAndForwardQueue;
import com.factory.edge.buffer.TelemetryRecord;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@Testcontainers
@Tag("EDGE-MQTT")
class MqttTelemetryLinkTest {

    @Container
    static GenericContainer<?> mosquitto = new GenericContainer<>(DockerImageName.parse("eclipse-mosquitto:2"))
            .withExposedPorts(1883)
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("mosquitto-test.conf"),
                    "/mosquitto/config/mosquitto.conf");

    @TempDir
    Path tempDir;

    @Test
    void brokerDownQueuesThenLiveLinkReplaysEnvelope() throws Exception {
        Path db = tempDir.resolve("mqtt-replay.db");
        ObjectMapper objectMapper = new ObjectMapper();

        MqttTelemetryLink down = new MqttTelemetryLink(unreachableProps(), objectMapper);
        try (StoreAndForwardQueue queue = newQueue(down, db, objectMapper)) {
            down.bindBufferedCount(queue::depth);
            assertThat(down.health().status()).isEqualTo(EdgeLinkHealth.DISCONNECTED);
            assertThat(down.health().lastPublishAt()).isNull();

            queue.offer(valve(42.5));
            await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(queue.depth()).isEqualTo(1));
        } finally {
            down.shutdown();
        }

        String broker = "tcp://" + mosquitto.getHost() + ":" + mosquitto.getMappedPort(1883);
        MqttProperties liveProps = new MqttProperties();
        liveProps.setBrokerUrl(broker);
        liveProps.setClientId("edge-mqtt-live-" + UUID.randomUUID());
        liveProps.setReconnectInitialMs(200);
        liveProps.setReconnectMaxMs(500);

        CopyOnWriteArrayList<JsonNode> envelopes = new CopyOnWriteArrayList<>();
        try (MqttClient subscriber = new MqttClient(broker, "edge-sub-" + UUID.randomUUID())) {
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            subscriber.connect(options);
            subscriber.subscribe(
                    "site/+/line/+/telemetry",
                    (topic, message) -> envelopes.add(
                            objectMapper.readTree(new String(message.getPayload(), StandardCharsets.UTF_8))));

            MqttTelemetryLink liveLink = new MqttTelemetryLink(liveProps, objectMapper);
            try (StoreAndForwardQueue queue2 = newQueue(liveLink, db, objectMapper)) {
                liveLink.bindBufferedCount(queue2::depth);

                await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
                    assertThat(liveLink.isAvailable()).isTrue();
                    assertThat(queue2.depth()).isZero();
                    assertThat(envelopes).isNotEmpty();
                });

                JsonNode root = envelopes.getFirst();
                assertThat(root.get("siteId").asText()).isEqualTo("INDORE");
                assertThat(root.get("lineId").asText()).isEqualTo("LINE-01");
                assertThat(root.get("timestamp").asText()).endsWith("Z");
                JsonNode reading = root.get("readings").get(0);
                assertThat(reading.get("tagCode").asText()).isEqualTo("VALVE_101_POS");
                assertThat(reading.get("nodeId").asText()).isEqualTo("nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=1001");
                assertThat(reading.get("value").asDouble()).isEqualTo(42.5d);
                assertThat(reading.get("quality").asText()).isEqualTo("GOOD");

                EdgeLinkHealth health = liveLink.health();
                assertThat(health.status()).isEqualTo(EdgeLinkHealth.CONNECTED);
                assertThat(health.bufferedCount()).isZero();
                assertThat(health.lastPublishAt()).isNotNull();
            } finally {
                liveLink.shutdown();
            }
            subscriber.disconnect();
        }
    }

    @Test
    void unavailableLinkKeepsQueuedHeadAndNullLastPublish() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        MqttTelemetryLink down = new MqttTelemetryLink(unreachableProps(), objectMapper);
        try (StoreAndForwardQueue queue =
                newQueue(down, tempDir.resolve("mqtt-fail-down.db"), objectMapper)) {
            down.bindBufferedCount(queue::depth);
            queue.offer(valve(1.0));
            await().atMost(Duration.ofSeconds(2))
                    .untilAsserted(() -> assertThat(queue.depth()).isEqualTo(1));
            assertThat(down.health().status()).isEqualTo(EdgeLinkHealth.DISCONNECTED);
            assertThat(down.health().lastPublishAt()).isNull();
            assertThat(down.health().bufferedCount()).isEqualTo(1);
        } finally {
            down.shutdown();
        }
    }

    private static StoreAndForwardQueue newQueue(
            MqttTelemetryLink link, Path db, ObjectMapper objectMapper) {
        BufferProperties buffer = new BufferProperties();
        buffer.setPath(db.toString());
        buffer.setMaxRecords(100);
        buffer.setDrainBatchSize(32);
        buffer.setDrainPauseMs(10);
        return new StoreAndForwardQueue(link, buffer, objectMapper);
    }

    private static MqttProperties unreachableProps() {
        MqttProperties props = new MqttProperties();
        props.setBrokerUrl("tcp://127.0.0.1:1");
        props.setClientId("edge-mqtt-down-" + UUID.randomUUID());
        props.setReconnectInitialMs(5_000);
        props.setReconnectMaxMs(5_000);
        return props;
    }

    private static TelemetryRecord valve(double value) {
        return new TelemetryRecord(
                "INDORE",
                "LINE-01",
                "VALVE_101_POS",
                "nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=1001",
                value,
                TelemetryRecord.TYPE_DOUBLE,
                "GOOD",
                null,
                Instant.parse("2026-11-19T12:00:01.200Z"),
                Instant.parse("2026-11-19T12:00:01.250Z"),
                null);
    }
}
