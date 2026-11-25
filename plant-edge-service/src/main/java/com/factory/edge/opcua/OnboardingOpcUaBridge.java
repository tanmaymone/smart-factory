package com.factory.edge.opcua;

import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.springframework.stereotype.Component;

/**
 * Package boundary for the onboarding probe. {@link OpcUaClientFactory} is package-private, so the
 * probe service in {@code com.factory.edge.onboarding} reaches its probe overloads through this
 * bean. Teardown stops at the probe client's own {@code disconnect()}: the probe shares the JVM
 * stack with the live {@link OpcUaProbe} subscription client.
 */
@Component
public class OnboardingOpcUaBridge {

    /** Null when SignAndEncrypt can be attempted; the message otherwise. */
    public String pkiFailure(String pkiDir) {
        return OpcUaClientFactory.probePkiFailure(pkiDir);
    }

    /** Must be called from an {@code opcua-probe-io} worker, never the probe thread. */
    public EndpointDescription discoverAndRewriteForProbe(
            String canonicalDialUrl, String securityMode, long timeoutMs) throws Exception {
        return OpcUaClientFactory.discoverAndRewriteForProbe(
                canonicalDialUrl, securityMode, timeoutMs);
    }

    public OpcUaClient createForProbe(
            EndpointDescription rewritten,
            String canonicalDialUrl,
            String securityMode,
            String pkiDir,
            long timeoutMs) {
        return OpcUaClientFactory.createForProbe(
                rewritten, canonicalDialUrl, securityMode, pkiDir, timeoutMs);
    }
}
