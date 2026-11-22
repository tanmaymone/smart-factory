package com.factory.edge.opcua;

import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.eclipse.milo.opcua.stack.core.util.EndpointUtil;

/**
 * Rewrites the host and port of a discovered endpoint to the operator-configured URL so Prosys
 * advertisements of {@code Tanmay} vs {@code localhost} do not change where the client connects.
 */
public final class EndpointRewriter {

    private EndpointRewriter() {}

    public static EndpointDescription rewrite(EndpointDescription advertised, String configuredUrl) {
        if (advertised == null) {
            throw new OpcUaProbeException("Advertised endpoint is null");
        }
        if (configuredUrl == null || configuredUrl.isBlank()) {
            throw new OpcUaProbeException("Configured endpoint URL is blank");
        }
        String host = EndpointUtil.getHost(configuredUrl);
        if (host == null || host.isBlank()) {
            throw new OpcUaProbeException("Configured endpoint URL has no host: " + configuredUrl);
        }
        int port = EndpointUtil.getPort(configuredUrl);
        return EndpointUtil.updateUrl(advertised, host, port);
    }
}
