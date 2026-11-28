package com.factory.edge.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.factory.edge.mqtt.MqttProperties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

/**
 * SF-M36: isolated snapshot paths and MQTT client/command suffixes for multi-plant hosts.
 * Buffer and command isolation are covered in {@code StoreAndForwardQueueTest} and
 * {@code CommandDispatcherTest} (same milestones, package-local constructors).
 */
@Tag("SF-M36")
class MultiPlantProvisioningIsolationTest {

  private static final String NSU = "nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=1007";

  @TempDir
  Path temp;

  @Test
  void distinctSnapshotsAndMqttClientCommandSuffixes() throws Exception {
    Path snapshotA = temp.resolve("indore/edge-config.json");
    Path snapshotB = temp.resolve("site-cold/edge-config.json");
    EdgeConfigSnapshotStore storeA = store(snapshotA, "INDORE");
    EdgeConfigSnapshotStore storeB = store(snapshotB, "SITE-COLD");
    storeA.write(manifest("INDORE", 1, "hash-a"));
    storeB.write(manifest("SITE-COLD", 2, "hash-b"));

    assertThat(storeA.read()).isPresent();
    assertThat(storeB.read()).isPresent();
    assertThat(store(snapshotA, "SITE-COLD").read()).isEmpty();
    assertThat(snapshotA).exists();

    MqttProperties mqttA = mqtt("plant-edge-indore");
    MqttProperties mqttB = mqtt("plant-edge-site-cold");
    assertThat(mqttA.getClientId() + "-commands").isEqualTo("plant-edge-indore-commands");
    assertThat(mqttB.getClientId() + "-commands").isEqualTo("plant-edge-site-cold-commands");
    assertThat(mqttA.getClientId()).isNotEqualTo(mqttB.getClientId());
  }

  private EdgeConfigSnapshotStore store(Path path, String siteId) {
    EdgeConfigProperties properties = new EdgeConfigProperties();
    properties.setConfigSyncEnabled(true);
    properties.setSiteId(siteId);
    properties.setSnapshotPath(path.toString());
    return new EdgeConfigSnapshotStore(properties, new com.fasterxml.jackson.databind.ObjectMapper());
  }

  private EdgeManifest manifest(String siteId, long version, String contentHash) throws Exception {
    var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
    String raw =
        """
        {"siteId":"%s","siteLifecycle":"ACTIVE","version":%d,"contentHash":"%s",
         "lines":[{"lineId":"LINE-01","tags":[
           {"tagCode":"VALVE_101_POS","externalAddress":"%s","deadband":0.5,
            "writable":true,"protocol":"OPC_UA","dataType":"FLOAT"}]}]}
        """
            .formatted(siteId, version, contentHash, NSU);
    return EdgeManifest.parse(raw, mapper.readTree(raw));
  }

  private static MqttProperties mqtt(String clientId) {
    MqttProperties properties = new MqttProperties();
    properties.setClientId(clientId);
    properties.setBrokerUrl("tcp://127.0.0.1:1883");
    return properties;
  }
}