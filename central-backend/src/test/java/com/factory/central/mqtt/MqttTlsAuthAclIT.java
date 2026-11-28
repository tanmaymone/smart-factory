package com.factory.central.mqtt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@Testcontainers
@Tag("GW-M41")
class MqttTlsAuthAclIT {

    private static final int SUBACK_FAILURE_QOS = 128;

    @Container
    static GenericContainer<?> mosquitto =
            new GenericContainer<>(DockerImageName.parse("eclipse-mosquitto:2"))
                    .withExposedPorts(8883)
                    .withCopyFileToContainer(
                            MountableFile.forClasspathResource("m41-tls/mosquitto.conf"),
                            "/mosquitto/config/mosquitto.conf")
                    .withCopyFileToContainer(
                            MountableFile.forClasspathResource("m41-tls/passwd"), "/mosquitto/config/passwd")
                    .withCopyFileToContainer(
                            MountableFile.forClasspathResource("m41-tls/acl"), "/mosquitto/config/acl")
                    .withCopyFileToContainer(
                            MountableFile.forClasspathResource("m41-tls/ca.crt"), "/mosquitto/certs/ca.crt")
                    .withCopyFileToContainer(
                            MountableFile.forClasspathResource("m41-tls/broker.crt"), "/mosquitto/certs/broker.crt")
                    .withCopyFileToContainer(
                            MountableFile.forClasspathResource("m41-tls/broker.key"), "/mosquitto/certs/broker.key");

    private String brokerUrl() {
        return "ssl://"
                + mosquitto.getHost()
                + ":"
                + mosquitto.getMappedPort(8883);
    }

    private MqttConnectOptions optionsFor(String user, String pass) throws Exception {
        MqttProperties.Tls tls = new MqttProperties.Tls();
        Path ca = Path.of(MqttTlsAuthAclIT.class.getResource("/m41-tls/ca.crt").toURI());
        tls.setCaCertPath(ca.toString());
        tls.setVerifyHostname(false);
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[] {brokerUrl()});
        options.setUserName(user);
        options.setPassword(pass.toCharArray());
        options.setSocketFactory(MqttTlsSupport.buildSocketFactory(tls));
        options.setHttpsHostnameVerificationEnabled(false);
        return options;
    }

    @Test
    void anonymousClientRejected() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[] {brokerUrl()});
        try {
            options.setSocketFactory(
                    MqttTlsSupport.buildSocketFactory(caTls()));
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
        options.setHttpsHostnameVerificationEnabled(false);
        assertThatThrownBy(() -> connectClient("anon-" + UUID.randomUUID(), options))
                .isInstanceOf(MqttException.class)
                .satisfies(ex -> {
                    int code = ((MqttException) ex).getReasonCode();
                    assertThat(code == MqttException.REASON_CODE_FAILED_AUTHENTICATION
                                    || code == MqttException.REASON_CODE_NOT_AUTHORIZED)
                            .isTrue();
                });
    }

    @Test
    void crossSiteTelemetryPublishDeniedAtBroker() throws Exception {
        List<String> seen = new CopyOnWriteArrayList<>();
        String centralId = "central-sub-" + UUID.randomUUID();
        MqttClient central =
                new MqttClient(brokerUrl(), centralId, new MemoryPersistence());
        central.connect(optionsFor("central-backend", "central-dev"));
        central.subscribe(
                "site/+/line/+/telemetry",
                (topic, message) -> seen.add(new String(message.getPayload(), StandardCharsets.UTF_8)));

        String edgeId = "indore-pub-" + UUID.randomUUID();
        MqttClient edge = new MqttClient(brokerUrl(), edgeId, new MemoryPersistence());
        edge.connect(optionsFor("edge-INDORE", "edge-indore-dev"));
        MqttMessage msg = new MqttMessage("{\"siteId\":\"PUNE\"}".getBytes(StandardCharsets.UTF_8));
        msg.setQos(1);
        edge.publish("site/PUNE/line/LINE-01/telemetry", msg);

        MqttMessage allowed = new MqttMessage("{\"siteId\":\"INDORE\"}".getBytes(StandardCharsets.UTF_8));
        allowed.setQos(1);
        edge.publish("site/INDORE/line/LINE-01/telemetry", allowed);

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(seen).hasSize(1));
        assertThat(seen.get(0)).contains("INDORE");

        IMqttToken deniedSub = edge.subscribeWithResponse("site/PUNE/line/+/commands", 1);
        if (deniedSub.getGrantedQos() != null
                && deniedSub.getGrantedQos().length > 0
                && deniedSub.getGrantedQos()[0] == SUBACK_FAILURE_QOS) {
            assertThat(deniedSub.getGrantedQos()).containsExactly(SUBACK_FAILURE_QOS);
        }

        edge.disconnect();
        edge.close();
        central.disconnect();
        central.close();
    }

    private static MqttProperties.Tls caTls() throws Exception {
        MqttProperties.Tls tls = new MqttProperties.Tls();
        tls.setCaCertPath(
                Path.of(MqttTlsAuthAclIT.class.getResource("/m41-tls/ca.crt").toURI()).toString());
        tls.setVerifyHostname(false);
        return tls;
    }

    private static void connectClient(String clientId, MqttConnectOptions options) throws MqttException {
        try (MqttClient client = new MqttClient(options.getServerURIs()[0], clientId, new MemoryPersistence())) {
            client.connect(options);
        }
    }
}