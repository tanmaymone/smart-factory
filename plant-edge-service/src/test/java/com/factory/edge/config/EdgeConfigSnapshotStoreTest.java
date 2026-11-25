package com.factory.edge.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("EDGE-CONFIG")
class EdgeConfigSnapshotStoreTest {

    private static final String NSU = "nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=1007";

    private final ObjectMapper mapper = new ObjectMapper();

    @TempDir Path temp;

    @Test
    void writeThenReadRoundTripsManifestAndTags() throws Exception {
        Path path = temp.resolve("edge-config.json");
        EdgeConfigSnapshotStore store = store(path, "INDORE");

        store.write(manifest("INDORE", 7, "hash-7"));

        EdgeConfigSnapshotStore.LoadedSnapshot loaded = store.read().orElseThrow();
        assertThat(loaded.manifest().version()).isEqualTo(7L);
        assertThat(loaded.manifest().contentHash()).isEqualTo("hash-7");
        assertThat(loaded.manifest().tags())
                .singleElement()
                .satisfies(
                        tag -> {
                            assertThat(tag.getLineId()).isEqualTo("LINE-01");
                            assertThat(tag.getTagCode()).isEqualTo("VALVE_101_POS");
                            assertThat(tag.getNodeId()).isEqualTo(NSU);
                            assertThat(tag.getDeadband()).isEqualTo(0.5);
                            assertThat(tag.isWritable()).isTrue();
                        });
        assertThat(loaded.savedAt()).isNotNull();
    }

    @Test
    void missingFileReadsAsAbsent() {
        assertThat(store(temp.resolve("nope.json"), "INDORE").read()).isEmpty();
    }

    @Test
    void checksumMismatchIsQuarantinedAndTreatedAsAbsent() throws Exception {
        Path path = temp.resolve("edge-config.json");
        EdgeConfigSnapshotStore store = store(path, "INDORE");
        store.write(manifest("INDORE", 3, "hash-3"));

        String corrupted =
                Files.readString(path, StandardCharsets.UTF_8).replace("hash-3", "hash-tampered");
        Files.writeString(path, corrupted, StandardCharsets.UTF_8);

        assertThat(store.read()).isEmpty();
        assertThat(path).doesNotExist();
        assertThat(path.resolveSibling(path.getFileName() + ".bad")).exists();
    }

    @Test
    void snapshotForAnotherSiteIsIgnoredAndLeftInPlace() throws Exception {
        Path path = temp.resolve("edge-config.json");
        store(path, "PUNE").write(manifest("PUNE", 2, "hash-2"));

        assertThat(store(path, "INDORE").read()).isEmpty();
        assertThat(path).exists();
    }

    @Test
    void writeRejectsSiteMismatchAndUnversionedManifest() {
        EdgeConfigSnapshotStore store = store(temp.resolve("edge-config.json"), "INDORE");

        assertThatThrownBy(() -> store.write(manifest("PUNE", 1, "hash-1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match configured");
        assertThatThrownBy(() -> store.write(manifest("INDORE", 0, "hash-0")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("version must be >= 1");
        assertThatThrownBy(() -> store.write(manifest("INDORE", 1, "")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contentHash is required");
    }

    private EdgeConfigSnapshotStore store(Path path, String siteId) {
        EdgeConfigProperties properties = new EdgeConfigProperties();
        properties.setConfigSyncEnabled(true);
        properties.setSiteId(siteId);
        properties.setSnapshotPath(path.toString());
        return new EdgeConfigSnapshotStore(properties, mapper);
    }

    private EdgeManifest manifest(String siteId, long version, String contentHash) throws Exception {
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
}
