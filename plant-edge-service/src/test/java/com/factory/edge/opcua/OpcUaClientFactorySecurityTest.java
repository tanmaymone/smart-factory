package com.factory.edge.opcua;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.transport.TransportProfile;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.UserTokenType;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.UserTokenPolicy;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("EDGE-SEC")
class OpcUaClientFactorySecurityTest {

    @TempDir
    Path tempDir;

    @Test
    void noneSelectedOnlyWhenConfigured() {
        OpcUaProperties.SecurityConfig security = new OpcUaProperties.SecurityConfig();
        security.setPolicy("None");
        security.setIdentity("anonymous");

        EndpointDescription none =
                endpoint(SecurityPolicy.None.getUri(), MessageSecurityMode.None);
        EndpointDescription secure =
                endpoint(SecurityPolicy.Basic256Sha256.getUri(), MessageSecurityMode.SignAndEncrypt);

        EndpointDescription selected =
                OpcUaClientFactory.selectEndpoint(List.of(secure, none), "opc.tcp://x", security);
        assertThat(selected.getSecurityPolicyUri()).isEqualTo(SecurityPolicy.None.getUri());
        assertThat(selected.getSecurityMode()).isEqualTo(MessageSecurityMode.None);
    }

    @Test
    void basic256SelectsSignAndEncryptAndDoesNotFallBackToNone() {
        OpcUaProperties.SecurityConfig security = new OpcUaProperties.SecurityConfig();
        security.setPolicy("Basic256Sha256");
        security.setIdentity("anonymous");

        EndpointDescription none =
                endpoint(SecurityPolicy.None.getUri(), MessageSecurityMode.None);
        EndpointDescription secure =
                endpoint(SecurityPolicy.Basic256Sha256.getUri(), MessageSecurityMode.SignAndEncrypt);

        EndpointDescription selected =
                OpcUaClientFactory.selectEndpoint(List.of(none, secure), "opc.tcp://x", security);
        assertThat(selected.getSecurityPolicyUri())
                .isEqualTo(SecurityPolicy.Basic256Sha256.getUri());
        assertThat(selected.getSecurityMode()).isEqualTo(MessageSecurityMode.SignAndEncrypt);

        assertThatThrownBy(
                        () ->
                                OpcUaClientFactory.selectEndpoint(
                                        List.of(none), "opc.tcp://x", security))
                .isInstanceOf(OpcUaProbeException.class)
                .hasMessageContaining("Basic256Sha256");
    }

    @Test
    void emptyTrustDirFailsBeforeDiscovery() throws Exception {
        Path pki = tempDir.resolve("pki");
        Files.createDirectories(pki.resolve("own"));
        Files.createDirectories(pki.resolve("trusted").resolve("certs"));
        Files.write(pki.resolve("own").resolve("cert.der"), new byte[] {1, 2, 3});
        Files.writeString(
                pki.resolve("own").resolve("private-key.pem"),
                "-----BEGIN PRIVATE KEY-----\nQQ==\n-----END PRIVATE KEY-----\n");

        assertThatThrownBy(() -> OpcUaPkiMaterial.load(pki.toString()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("trusted/certs is empty");
    }

    @Test
    void missingPkiDirFailsFastAsIllegalState() {
        assertThatThrownBy(() -> OpcUaPkiMaterial.load(tempDir.resolve("missing").toString()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("readable directory");
    }

    private static EndpointDescription endpoint(String policyUri, MessageSecurityMode mode) {
        UserTokenPolicy anonymous =
                new UserTokenPolicy("anonymous", UserTokenType.Anonymous, null, null, null);
        return new EndpointDescription(
                "opc.tcp://127.0.0.1:4840",
                null,
                ByteString.NULL_VALUE,
                mode,
                policyUri,
                new UserTokenPolicy[] {anonymous},
                TransportProfile.TCP_UASC_UABINARY.getUri(),
                Unsigned.ubyte(0));
    }
}
