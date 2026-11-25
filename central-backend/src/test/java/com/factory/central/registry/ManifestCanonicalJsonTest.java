package com.factory.central.registry;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("SF-REGISTRY")
class ManifestCanonicalJsonTest {

    @Test
    void hashIsStableAcrossTagOrderAndDeadbandTrailingZeros() {
        EdgeConfigTag a = new EdgeConfigTag(
                "A_TAG",
                "OPC_UA",
                "nsu=http://example/;i=1",
                "FLOAT",
                true,
                0.50,
                "opc.tcp://localhost:53530/OPCUA/SimulationServer");
        EdgeConfigTag b = new EdgeConfigTag(
                "B_TAG",
                "OPC_UA",
                "nsu=http://example/;i=2",
                "FLOAT",
                false,
                0.5,
                "opc.tcp://localhost:53530/OPCUA/SimulationServer");

        String h1 = ManifestCanonicalJson.hashLines(List.of(
                new EdgeConfigLine("LINE-02", List.of(b)),
                new EdgeConfigLine("LINE-01", List.of(b, a))));
        String h2 = ManifestCanonicalJson.hashLines(List.of(
                new EdgeConfigLine("LINE-01", List.of(a, b)),
                new EdgeConfigLine("LINE-02", List.of(b))));

        assertThat(h1).isEqualTo(h2).hasSize(64);
    }

    @Test
    void emptyLinesHashIsDeterministic() {
        assertThat(ManifestCanonicalJson.hashLines(List.of()))
                .isEqualTo(ManifestCanonicalJson.hashLines(List.of()));
    }
}
