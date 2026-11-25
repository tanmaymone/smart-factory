package com.factory.edge.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class EdgeConfigClient {

    private final EdgeConfigProperties properties;
    private final RestClient restClient;

    @Autowired
    public EdgeConfigClient(EdgeConfigProperties properties) {
        this.properties = properties;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofMillis(Math.max(100L, properties.getConfigTimeoutMs())));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    EdgeConfigClient(EdgeConfigProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    public FetchResult fetch(Long fetchedVersion) {
        String url = baseUrl() + "/api/edge/" + properties.getNormalizedSiteId() + "/config";
        var request = restClient.get().uri(URI.create(url)).accept(MediaType.APPLICATION_JSON);
        if (fetchedVersion != null && fetchedVersion > 0) {
            request = request.header(HttpHeaders.IF_NONE_MATCH, "\"v" + fetchedVersion + "\"");
        }
        try {
            return request.exchange((req, res) -> {
                HttpStatusCode status = res.getStatusCode();
                if (status.value() == 304) {
                    return FetchResult.notModified();
                }
                if (status.value() == 404) {
                    return FetchResult.notFound();
                }
                if (!status.is2xxSuccessful()) {
                    return FetchResult.error("HTTP " + status.value());
                }
                byte[] body = res.getBody().readAllBytes();
                String raw = new String(body, java.nio.charset.StandardCharsets.UTF_8);
                return FetchResult.ok(raw);
            });
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                return FetchResult.notFound();
            }
            return FetchResult.error("HTTP " + ex.getStatusCode().value());
        } catch (Exception ex) {
            return FetchResult.error(ex.getMessage() == null ? "unreachable" : ex.getMessage());
        }
    }

    private String baseUrl() {
        String base = properties.getCentralBaseUrl().trim();
        if (base.endsWith("/")) {
            return base.substring(0, base.length() - 1);
        }
        return base;
    }

    public sealed interface FetchResult {
        record Ok(String rawBody) implements FetchResult {}

        record NotModified() implements FetchResult {}

        record NotFound() implements FetchResult {}

        record Error(String message) implements FetchResult {}

        static FetchResult ok(String raw) {
            return new Ok(raw);
        }

        static FetchResult notModified() {
            return new NotModified();
        }

        static FetchResult notFound() {
            return new NotFound();
        }

        static FetchResult error(String message) {
            return new Error(message);
        }
    }
}
