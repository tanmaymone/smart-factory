package com.factory.edge.opcua;

import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Thin facade so {@link OpcUaProbe} can publish health and observations without bloat. */
@Component
public class OpcUaDiagnosticsSink {

    private final OpcUaLinkHealth linkHealth;
    private final TagObservationStore observations;

    @Autowired
    public OpcUaDiagnosticsSink(OpcUaLinkHealth linkHealth, TagObservationStore observations) {
        this.linkHealth = linkHealth;
        this.observations = observations;
    }

    OpcUaDiagnosticsSink() {
        this(new OpcUaLinkHealth(), new TagObservationStore());
    }

    OpcUaLinkHealth linkHealth() {
        return linkHealth;
    }

    TagObservationStore observations() {
        return observations;
    }

    void markStarting() {
        linkHealth.markStarting();
    }

    void markUp() {
        linkHealth.markUp();
    }

    void markIdle() {
        linkHealth.markIdle();
    }

    void markStopped() {
        linkHealth.markStopped();
    }

    void markLost(String error) {
        linkHealth.markLost(error);
    }

    void markReconnectFailed(String error) {
        linkHealth.markReconnectFailed(error);
    }

    void onReading(TagReading reading, boolean emitted) {
        linkHealth.heartbeat(Instant.now());
        observations.record(reading, emitted);
    }
}
