package com.factory.edge.opcua;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.UserTokenType;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.UserTokenPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned;
import org.eclipse.milo.opcua.stack.core.types.structured.ApplicationDescription;
import org.eclipse.milo.opcua.stack.core.types.enumerated.ApplicationType;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("EDGE-OPC-PROBE")
class EndpointRewriterTest {

    @Test
    void rewritesTanmayAdvertisementToLocalhost() {
        EndpointDescription advertised =
                endpoint("opc.tcp://Tanmay:53530/OPCUA/SimulationServer");
        EndpointDescription rewritten =
                EndpointRewriter.rewrite(
                        advertised, "opc.tcp://localhost:53530/OPCUA/SimulationServer");

        assertThat(rewritten.getEndpointUrl())
                .isEqualTo("opc.tcp://localhost:53530/OPCUA/SimulationServer");
        assertThat(rewritten.getSecurityPolicyUri()).isEqualTo(advertised.getSecurityPolicyUri());
        assertThat(rewritten.getSecurityMode()).isEqualTo(advertised.getSecurityMode());
        assertThat(rewritten.getTransportProfileUri())
                .isEqualTo(advertised.getTransportProfileUri());
    }

    @Test
    void rewritesLocalhostAdvertisementToTanmay() {
        EndpointDescription advertised =
                endpoint("opc.tcp://localhost:53530/OPCUA/SimulationServer");
        EndpointDescription rewritten =
                EndpointRewriter.rewrite(
                        advertised, "opc.tcp://Tanmay:53530/OPCUA/SimulationServer");

        assertThat(rewritten.getEndpointUrl())
                .isEqualTo("opc.tcp://Tanmay:53530/OPCUA/SimulationServer");
    }

    private static EndpointDescription endpoint(String url) {
        ApplicationDescription server =
                new ApplicationDescription(
                        "urn:test",
                        "urn:test:product",
                        LocalizedText.english("test"),
                        ApplicationType.Server,
                        null,
                        null,
                        null);
        UserTokenPolicy anonymous =
                new UserTokenPolicy("anonymous", UserTokenType.Anonymous, null, null, null);
        return new EndpointDescription(
                url,
                server,
                ByteString.NULL_VALUE,
                MessageSecurityMode.None,
                SecurityPolicy.None.getUri(),
                new UserTokenPolicy[] {anonymous},
                "http://opcfoundation.org/UA-Profile/Transport/uatcp-uasc-uabinary",
                Unsigned.ubyte(0));
    }
}
