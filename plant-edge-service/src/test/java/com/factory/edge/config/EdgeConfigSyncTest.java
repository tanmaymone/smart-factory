package com.factory.edge.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;

@Tag("EDGE-CONFIG")
class EdgeConfigSyncTest {

    private static final String NSU = "nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=1007";

    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicInteger reconciles = new AtomicInteger();

    @TempDir Path temp;

    @Test
    void okBodySetsDesiredWritesSnapshotAndRequestsReconcile() {
        Fixture fixture = fixture("INDORE");
        fixture.client.queue(EdgeConfigClient.FetchResult.ok(body("INDORE", 4, "hash-4")));

        fixture.sync.syncOnce();

        assertThat(fixture.sync.desiredOrNull()).isNotNull();
        assertThat(fixture.sync.desiredOrNull().version()).isEqualTo(4L);
        assertThat(fixture.sync.desiredOrNull().tags())
                .extracting(tag -> tag.getLineId() + "|" + tag.getTagCode())
                .containsExactly("LINE-01|VALVE_101_POS");
        assertThat(fixture.sync.syncView().source()).isEqualTo(EdgeSyncView.SOURCE_CENTRAL);
        assertThat(fixture.sync.syncView().fetchedVersion()).isEqualTo(4L);
        assertThat(fixture.sync.syncView().lastError()).isNull();
        assertThat(reconciles.get()).isEqualTo(1);
        assertThat(fixture.store.read()).isPresent();
    }

    @Test
    void notModifiedKeepsVersionAndDoesNotReconcileAgain() {
        Fixture fixture = fixture("INDORE");
        fixture.client.queue(EdgeConfigClient.FetchResult.ok(body("INDORE", 4, "hash-4")));
        fixture.sync.syncOnce();
        assertThat(reconciles.get()).isEqualTo(1);

        fixture.client.queue(EdgeConfigClient.FetchResult.notModified());
        fixture.sync.syncOnce();

        assertThat(fixture.client.lastRequestedVersion).isEqualTo(4L);
        assertThat(fixture.sync.syncView().fetchedVersion()).isEqualTo(4L);
        assertThat(fixture.sync.syncView().contentHash()).isEqualTo("hash-4");
        assertThat(fixture.sync.syncView().lastError()).isNull();
        assertThat(reconciles.get()).isEqualTo(1);
    }

    @Test
    void transportErrorKeepsLastGoodDesiredAndRecordsError() {
        Fixture fixture = fixture("INDORE");
        fixture.client.queue(EdgeConfigClient.FetchResult.ok(body("INDORE", 4, "hash-4")));
        fixture.sync.syncOnce();

        fixture.client.queue(EdgeConfigClient.FetchResult.error("Connection refused"));
        fixture.sync.syncOnce();

        assertThat(fixture.sync.desiredOrNull().version()).isEqualTo(4L);
        assertThat(fixture.sync.syncView().lastError()).isEqualTo("Connection refused");
        assertThat(fixture.sync.syncView().fetchedVersion()).isEqualTo(4L);
        assertThat(reconciles.get()).isEqualTo(1);
    }

    @Test
    void notFoundOnFirstFetchLeavesDesiredNull() {
        Fixture fixture = fixture("INDORE");
        fixture.client.queue(EdgeConfigClient.FetchResult.notFound());

        fixture.sync.syncOnce();

        assertThat(fixture.sync.desiredOrNull()).isNull();
        assertThat(fixture.sync.syncView().source()).isEqualTo(EdgeSyncView.SOURCE_NONE);
        assertThat(fixture.sync.syncView().lastError()).isEqualTo("SITE_NOT_FOUND");
        assertThat(reconciles.get()).isZero();
    }

    @Test
    void manifestForAnotherSiteIsRejectedWithoutClobberingDesired() {
        Fixture fixture = fixture("INDORE");
        fixture.client.queue(EdgeConfigClient.FetchResult.ok(body("PUNE", 9, "hash-9")));

        fixture.sync.syncOnce();

        assertThat(fixture.sync.desiredOrNull()).isNull();
        assertThat(fixture.sync.syncView().lastError()).isEqualTo("invalid manifest body");
        assertThat(reconciles.get()).isZero();
    }

    @Test
    void bootstrapLoadsSnapshotThenReconcilesOnceMoreFromCentral() throws Exception {
        Fixture fixture = fixture("INDORE");
        fixture.store.write(manifest("INDORE", 2, "hash-2"));
        fixture.client.queue(EdgeConfigClient.FetchResult.ok(body("INDORE", 5, "hash-5")));

        fixture.sync.bootstrap();

        // One reconcile for the snapshot, one for the newer central manifest.
        assertThat(reconciles.get()).isEqualTo(2);
        assertThat(fixture.sync.desiredOrNull().version()).isEqualTo(5L);
        assertThat(fixture.sync.syncView().source()).isEqualTo(EdgeSyncView.SOURCE_CENTRAL);
    }

    @Test
    void bootstrapOfflineKeepsSnapshotAsLastKnownGood() throws Exception {
        Fixture fixture = fixture("INDORE");
        fixture.store.write(manifest("INDORE", 2, "hash-2"));
        fixture.client.queue(EdgeConfigClient.FetchResult.error("unreachable"));

        fixture.sync.bootstrap();

        assertThat(reconciles.get()).isEqualTo(1);
        assertThat(fixture.sync.desiredOrNull().version()).isEqualTo(2L);
        assertThat(fixture.sync.syncView().source()).isEqualTo(EdgeSyncView.SOURCE_LOCAL_SNAPSHOT);
        assertThat(fixture.sync.syncView().lastError()).isEqualTo("unreachable");
    }

    @Test
    void syncIsInertWhenDisabled() {
        EdgeConfigProperties properties = properties("INDORE");
        properties.setConfigSyncEnabled(false);
        FakeClient client = new FakeClient(properties);
        EdgeConfigSync sync =
                new EdgeConfigSync(
                        properties,
                        client,
                        new EdgeConfigSnapshotStore(properties, mapper),
                        mapper,
                        hookProvider());

        sync.onReady();
        sync.syncOnce();

        assertThat(sync.desiredOrNull()).isNull();
        assertThat(sync.syncView().source()).isEqualTo(EdgeSyncView.SOURCE_STATIC_YAML);
        assertThat(client.calls).isZero();
        assertThat(reconciles.get()).isZero();
    }

    private Fixture fixture(String siteId) {
        EdgeConfigProperties properties = properties(siteId);
        FakeClient client = new FakeClient(properties);
        EdgeConfigSnapshotStore store = new EdgeConfigSnapshotStore(properties, mapper);
        return new Fixture(
                client,
                store,
                new EdgeConfigSync(properties, client, store, mapper, hookProvider()));
    }

    private EdgeConfigProperties properties(String siteId) {
        EdgeConfigProperties properties = new EdgeConfigProperties();
        properties.setConfigSyncEnabled(true);
        properties.setSiteId(siteId);
        properties.setSnapshotPath(temp.resolve(siteId + "-edge-config.json").toString());
        return properties;
    }

    private EdgeManifest manifest(String siteId, long version, String hash) throws Exception {
        String raw = body(siteId, version, hash);
        return EdgeManifest.parse(raw, mapper.readTree(raw));
    }

    private static String body(String siteId, long version, String hash) {
        return """
               {"siteId":"%s","siteLifecycle":"ACTIVE","version":%d,"contentHash":"%s",
                "lines":[{"lineId":"LINE-01","tags":[
                  {"tagCode":"VALVE_101_POS","externalAddress":"%s","deadband":0.5,
                   "writable":true,"protocol":"OPC_UA","dataType":"FLOAT"}]}]}
               """
                .formatted(siteId, version, hash, NSU);
    }

    private record Fixture(FakeClient client, EdgeConfigSnapshotStore store, EdgeConfigSync sync) {}

    /** Replays queued results and records the conditional-GET version the sync asked for. */
    private static final class FakeClient extends EdgeConfigClient {

        private final Deque<FetchResult> results = new ArrayDeque<>();
        private Long lastRequestedVersion;
        private int calls;

        private FakeClient(EdgeConfigProperties properties) {
            super(properties, null);
        }

        void queue(FetchResult result) {
            results.add(result);
        }

        @Override
        public FetchResult fetch(Long fetchedVersion) {
            calls++;
            lastRequestedVersion = fetchedVersion;
            FetchResult next = results.poll();
            return next == null ? FetchResult.notModified() : next;
        }
    }

    private ObjectProvider<EdgeConfigReconcileHook> hookProvider() {
        EdgeConfigReconcileHook hook = reconciles::incrementAndGet;
        return new ObjectProvider<>() {
            @Override
            public EdgeConfigReconcileHook getObject() {
                return hook;
            }

            @Override
            public EdgeConfigReconcileHook getObject(Object... args) {
                return hook;
            }

            @Override
            public EdgeConfigReconcileHook getIfAvailable() {
                return hook;
            }

            @Override
            public EdgeConfigReconcileHook getIfUnique() {
                return hook;
            }

            @Override
            public java.util.stream.Stream<EdgeConfigReconcileHook> stream() {
                return List.of(hook).stream();
            }
        };
    }
}
