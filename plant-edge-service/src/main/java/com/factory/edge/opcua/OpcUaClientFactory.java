package com.factory.edge.opcua;

import static java.util.Objects.requireNonNullElse;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.eclipse.milo.opcua.sdk.client.DiscoveryClient;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.OpcUaClientConfig;
import org.eclipse.milo.opcua.sdk.client.OpcUaClientConfigBuilder;
import org.eclipse.milo.opcua.sdk.client.identity.AnonymousProvider;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.UserTokenType;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.UserTokenPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Discovers and selects the endpoint from {@code app.opcua.security.*}, rewrites host/port, and
 * builds one client. None + Anonymous only when configured; Basic256Sha256 requires readable PKI
 * before discovery.
 */
final class OpcUaClientFactory {

    private static final Logger log = LoggerFactory.getLogger(OpcUaClientFactory.class);

    private OpcUaClientFactory() {}

    static OpcUaClient create(OpcUaProperties properties) {
        OpcUaProperties.SecurityConfig security = properties.getSecurity();
        security.validate();

        OpcUaPkiMaterial.Loaded pki = null;
        if (security.isBasic256Sha256()) {
            // Config fail-fast before discovery — must not become OpcUaUnavailableException/BACKOFF.
            pki = OpcUaPkiMaterial.load(security.getPkiDir());
        } else if (!security.isNone() || !security.isAnonymous()) {
            throw new IllegalStateException(
                    "app.opcua.security.policy/identity combination is not supported");
        }

        String configuredUrl = properties.getEndpointUrl();
        long timeoutMs = properties.getRequestTimeoutMs();
        EndpointDescription rewritten = discoverAndRewrite(configuredUrl, timeoutMs, security);
        try {
            OpcUaClientConfigBuilder builder =
                    OpcUaClientConfig.builder()
                            .setEndpoint(rewritten)
                            .setIdentityProvider(AnonymousProvider.INSTANCE)
                            .setRequestTimeout(uint((int) timeoutMs));
            if (pki != null) {
                builder.setKeyPair(pki.keyPair())
                        .setCertificate(pki.certificate())
                        .setCertificateValidator(pki.certificateValidator());
            }
            return OpcUaClient.create(
                    builder.build(),
                    transport -> {
                        transport.setConnectTimeout(uint((int) timeoutMs));
                        transport.setAcknowledgeTimeout(uint((int) timeoutMs));
                    });
        } catch (IllegalStateException e) {
            closeQuietly(pki);
            throw e;
        } catch (Exception e) {
            closeQuietly(pki);
            throw new OpcUaProbeException(
                    "OPC UA client create failed for " + configuredUrl + ": " + e.getMessage(), e);
        }
    }

    static void connect(OpcUaClient client, OpcUaProperties properties) {
        String configuredUrl = properties.getEndpointUrl();
        long timeoutMs = properties.getRequestTimeoutMs();
        try {
            client.connectAsync().get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new OpcUaUnavailableException(
                    "OPC UA connect timed out after " + timeoutMs + "ms for " + configuredUrl, e);
        } catch (ExecutionException e) {
            throw new OpcUaUnavailableException(
                    "OPC UA connect failed for " + configuredUrl + ": " + rootMessage(e), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OpcUaProbeException("OPC UA connect interrupted for " + configuredUrl, e);
        }
    }

    private static EndpointDescription discoverAndRewrite(
            String configuredUrl, long timeoutMs, OpcUaProperties.SecurityConfig security) {
        List<EndpointDescription> endpoints;
        try {
            endpoints =
                    DiscoveryClient.getEndpoints(
                                    configuredUrl,
                                    builder -> {
                                        builder.setConnectTimeout(uint((int) timeoutMs));
                                        builder.setAcknowledgeTimeout(uint((int) timeoutMs));
                                    })
                            .get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new OpcUaUnavailableException(
                    "OPC UA discovery timed out after " + timeoutMs + "ms for " + configuredUrl, e);
        } catch (ExecutionException e) {
            throw new OpcUaUnavailableException(
                    "OPC UA discovery failed for " + configuredUrl + ": " + rootMessage(e), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OpcUaProbeException("OPC UA discovery interrupted for " + configuredUrl, e);
        }

        EndpointDescription selected = selectEndpoint(endpoints, configuredUrl, security);
        EndpointDescription rewritten = EndpointRewriter.rewrite(selected, configuredUrl);
        log.info(
                "OPC UA endpoints configured={} advertised={} rewritten={} policy={}",
                configuredUrl,
                selected.getEndpointUrl(),
                rewritten.getEndpointUrl(),
                security.getPolicy());
        return rewritten;
    }

    static EndpointDescription selectEndpoint(
            List<EndpointDescription> endpoints,
            String configuredUrl,
            OpcUaProperties.SecurityConfig security) {
        if (security.isNone()) {
            return selectAnonymousNone(endpoints, configuredUrl);
        }
        if (security.isBasic256Sha256()) {
            return selectBasic256Sha256SignAndEncrypt(endpoints, configuredUrl);
        }
        throw new IllegalStateException(
                "Unsupported app.opcua.security.policy=" + security.getPolicy());
    }

    private static EndpointDescription selectAnonymousNone(
            List<EndpointDescription> endpoints, String configuredUrl) {
        return endpoints.stream()
                .filter(e -> SecurityPolicy.None.getUri().equals(e.getSecurityPolicyUri()))
                .filter(e -> e.getSecurityMode() == MessageSecurityMode.None)
                .filter(OpcUaClientFactory::allowsAnonymous)
                .findFirst()
                .orElseThrow(
                        () ->
                                new OpcUaProbeException(
                                        "No SecurityPolicy.None + Anonymous endpoint at "
                                                + configuredUrl
                                                + "; discovered policies="
                                                + policies(endpoints)));
    }

    private static EndpointDescription selectBasic256Sha256SignAndEncrypt(
            List<EndpointDescription> endpoints, String configuredUrl) {
        return endpoints.stream()
                .filter(e -> SecurityPolicy.Basic256Sha256.getUri().equals(e.getSecurityPolicyUri()))
                .filter(e -> e.getSecurityMode() == MessageSecurityMode.SignAndEncrypt)
                .filter(OpcUaClientFactory::allowsAnonymous)
                .findFirst()
                .orElseThrow(
                        () ->
                                new OpcUaProbeException(
                                        "No Basic256Sha256 SignAndEncrypt + Anonymous endpoint at "
                                                + configuredUrl
                                                + "; discovered policies="
                                                + policies(endpoints)));
    }

    private static boolean allowsAnonymous(EndpointDescription endpoint) {
        return Arrays.stream(
                        requireNonNullElse(endpoint.getUserIdentityTokens(), new UserTokenPolicy[0]))
                .anyMatch(p -> p.getTokenType() == UserTokenType.Anonymous);
    }

    private static String policies(List<EndpointDescription> endpoints) {
        return endpoints.stream()
                .map(
                        e ->
                                e.getSecurityPolicyUri()
                                        + "/"
                                        + e.getSecurityMode())
                .collect(Collectors.joining(", "));
    }

    private static void closeQuietly(OpcUaPkiMaterial.Loaded pki) {
        if (pki == null || pki.trustListCloseable() == null) {
            return;
        }
        try {
            pki.trustListCloseable().close();
        } catch (Exception ignored) {
            // best-effort
        }
    }

    private static String rootMessage(ExecutionException e) {
        Throwable cause = e.getCause() == null ? e : e.getCause();
        return cause.getMessage() == null ? cause.toString() : cause.getMessage();
    }
}
