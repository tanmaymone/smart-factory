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
import org.eclipse.milo.opcua.sdk.client.identity.AnonymousProvider;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.enumerated.UserTokenType;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.UserTokenPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Discovers a None/Anonymous endpoint, rewrites host/port to the configured URL, and builds a
 * client. Connection uses a bounded timeout with no retry and no /discovery fallback.
 */
final class OpcUaClientFactory {

    private static final Logger log = LoggerFactory.getLogger(OpcUaClientFactory.class);

    private OpcUaClientFactory() {}

    static OpcUaClient create(OpcUaProperties properties) {
        String configuredUrl = properties.getEndpointUrl();
        long timeoutMs = properties.getRequestTimeoutMs();
        EndpointDescription rewritten = discoverAndRewrite(configuredUrl, timeoutMs);
        try {
            return OpcUaClient.create(
                    OpcUaClientConfig.builder()
                            .setEndpoint(rewritten)
                            .setIdentityProvider(AnonymousProvider.INSTANCE)
                            .setRequestTimeout(uint((int) timeoutMs))
                            .build(),
                    builder -> {
                        builder.setConnectTimeout(uint((int) timeoutMs));
                        builder.setAcknowledgeTimeout(uint((int) timeoutMs));
                    });
        } catch (Exception e) {
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
            throw new OpcUaProbeException(
                    "OPC UA connect timed out after " + timeoutMs + "ms for " + configuredUrl, e);
        } catch (ExecutionException e) {
            throw new OpcUaProbeException(
                    "OPC UA connect failed for " + configuredUrl + ": " + rootMessage(e), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OpcUaProbeException("OPC UA connect interrupted for " + configuredUrl, e);
        }
    }

    private static EndpointDescription discoverAndRewrite(String configuredUrl, long timeoutMs) {
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
            throw new OpcUaProbeException(
                    "OPC UA discovery timed out after " + timeoutMs + "ms for " + configuredUrl, e);
        } catch (ExecutionException e) {
            throw new OpcUaProbeException(
                    "OPC UA discovery failed for " + configuredUrl + ": " + rootMessage(e), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OpcUaProbeException("OPC UA discovery interrupted for " + configuredUrl, e);
        }

        EndpointDescription selected = selectAnonymousNone(endpoints, configuredUrl);
        EndpointDescription rewritten = EndpointRewriter.rewrite(selected, configuredUrl);
        log.info(
                "OPC UA endpoints configured={} advertised={} rewritten={}",
                configuredUrl,
                selected.getEndpointUrl(),
                rewritten.getEndpointUrl());
        return rewritten;
    }

    private static EndpointDescription selectAnonymousNone(
            List<EndpointDescription> endpoints, String configuredUrl) {
        return endpoints.stream()
                .filter(e -> SecurityPolicy.None.getUri().equals(e.getSecurityPolicyUri()))
                .filter(
                        e ->
                                Arrays.stream(
                                                requireNonNullElse(
                                                        e.getUserIdentityTokens(), new UserTokenPolicy[0]))
                                        .anyMatch(p -> p.getTokenType() == UserTokenType.Anonymous))
                .findFirst()
                .orElseThrow(
                        () ->
                                new OpcUaProbeException(
                                        "No SecurityPolicy.None + Anonymous endpoint at "
                                                + configuredUrl
                                                + "; discovered policies="
                                                + endpoints.stream()
                                                        .map(EndpointDescription::getSecurityPolicyUri)
                                                        .collect(Collectors.joining(", "))));
    }

    private static String rootMessage(ExecutionException e) {
        Throwable cause = e.getCause() == null ? e : e.getCause();
        return cause.getMessage() == null ? cause.toString() : cause.getMessage();
    }
}
