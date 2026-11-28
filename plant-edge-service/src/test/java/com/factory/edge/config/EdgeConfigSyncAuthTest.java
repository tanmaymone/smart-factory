package com.factory.edge.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.client.RestClient;

@Tag("EDGE-M42")
class EdgeConfigSyncAuthTest {

    private static final String TOKEN = "sf_edge_indore_test_token";
    private final ObjectMapper mapper = new ObjectMapper();

    @TempDir Path temp;

    @Test
    void sendsBearerTokenAndMaps401WithoutLeakingSecret() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/edge/INDORE/config", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = "unauthorized".getBytes();
            exchange.sendResponseHeaders(401, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            EdgeConfigProperties properties = properties(server.getAddress().getPort());
            properties.setConfigAuthToken(TOKEN);
            EdgeConfigClient client = new EdgeConfigClient(properties, RestClient.builder().build());

            EdgeConfigClient.FetchResult result = client.fetch(null);

            assertThat(authorization.get()).isEqualTo("Bearer " + TOKEN);
            assertThat(result).isInstanceOf(EdgeConfigClient.FetchResult.Error.class);
            assertThat(((EdgeConfigClient.FetchResult.Error) result).message()).isEqualTo("HTTP 401 UNAUTHENTICATED");
            assertThat(result.toString()).doesNotContain(TOKEN);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void authFailurePreservesSnapshotDesiredAndRecordsError() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/edge/INDORE/config", exchange -> {
            byte[] body = "forbidden".getBytes();
            exchange.sendResponseHeaders(403, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            EdgeConfigProperties properties = properties(server.getAddress().getPort());
            properties.setConfigAuthToken(TOKEN);
            EdgeConfigSnapshotStore store = new EdgeConfigSnapshotStore(properties, mapper);
            store.write(manifest("INDORE", 3, "hash-3"));

            EdgeConfigClient client = new EdgeConfigClient(properties, RestClient.builder().build());
            EdgeConfigSync sync =
                    new EdgeConfigSync(properties, client, store, mapper, emptyHookProvider());

            sync.bootstrap();
            assertThat(sync.desiredOrNull()).isNotNull();
            assertThat(sync.desiredOrNull().version()).isEqualTo(3L);
            Path snapshot = Path.of(properties.getSnapshotPath());
            assertThat(Files.readString(snapshot)).contains("hash-3");

            sync.syncOnce();

            assertThat(sync.syncView().lastError()).isEqualTo("HTTP 403 FORBIDDEN");
            assertThat(sync.syncView().lastError()).doesNotContain(TOKEN);
            assertThat(sync.desiredOrNull().version()).isEqualTo(3L);
            assertThat(Files.readString(snapshot)).contains("hash-3");
            assertThat(Files.list(temp).noneMatch(path -> path.toString().endsWith(".bad"))).isTrue();
        } finally {
            server.stop(0);
        }
    }

    private EdgeConfigProperties properties(int port) {
        EdgeConfigProperties properties = new EdgeConfigProperties();
        properties.setConfigSyncEnabled(true);
        properties.setSiteId("INDORE");
        properties.setCentralBaseUrl("http://127.0.0.1:" + port);
        properties.setSnapshotPath(temp.resolve("edge-config.json").toString());
        properties.setConfigTimeoutMs(2_000);
        return properties;
    }

    private EdgeManifest manifest(String siteId, long version, String hash) throws Exception {
        String raw =
                """
                {"siteId":"%s","siteLifecycle":"ACTIVE","version":%d,"contentHash":"%s",
                 "lines":[{"lineId":"LINE-01","tags":[
                   {"tagCode":"VALVE_101_POS","externalAddress":"ns=2;i=1","deadband":0.5,
                    "writable":true,"protocol":"OPC_UA","dataType":"FLOAT"}]}]}
                """
                        .formatted(siteId, version, hash);
        return EdgeManifest.parse(raw, mapper.readTree(raw));
    }

    private ObjectProvider<EdgeConfigReconcileHook> emptyHookProvider() {
        return new ObjectProvider<>() {
            @Override
            public EdgeConfigReconcileHook getObject() {
                return null;
            }

            @Override
            public EdgeConfigReconcileHook getObject(Object... args) {
                return null;
            }

            @Override
            public EdgeConfigReconcileHook getIfAvailable() {
                return null;
            }

            @Override
            public EdgeConfigReconcileHook getIfUnique() {
                return null;
            }

            @Override
            public java.util.stream.Stream<EdgeConfigReconcileHook> stream() {
                return List.<EdgeConfigReconcileHook>of().stream();
            }
        };
    }
}