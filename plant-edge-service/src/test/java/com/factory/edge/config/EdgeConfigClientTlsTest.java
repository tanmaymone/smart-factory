package com.factory.edge.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

@Tag("EDGE-M41")
class EdgeConfigClientTlsTest {

    @Test
    void buildsClientWithOptionalCaPath() throws Exception {
        EdgeConfigProperties properties = new EdgeConfigProperties();
        properties.setConfigSyncEnabled(false);
        properties.setCentralCaCertPath("");
        EdgeConfigClient client = new EdgeConfigClient(properties);
        assertThat(client).isNotNull();

        Path ca = Path.of(EdgeConfigClientTlsTest.class.getResource("/m41-tls/ca.crt").toURI());
        properties.setConfigSyncEnabled(true);
        properties.setSiteId("INDORE");
        properties.setCentralBaseUrl("https://localhost");
        properties.setCentralCaCertPath(ca.toString());
        properties.validate();
        client = new EdgeConfigClient(properties, RestClient.builder().build());
        assertThat(client).isNotNull();
    }
}