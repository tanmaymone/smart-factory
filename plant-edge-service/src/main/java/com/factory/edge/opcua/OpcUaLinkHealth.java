package com.factory.edge.opcua;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/** Thread-safe OPC UA link snapshot for edge diagnostics. */
@Component
public class OpcUaLinkHealth {

    public static final String CONNECTED = "CONNECTED";
    public static final String DISCONNECTED = "DISCONNECTED";

    public record Snapshot(String status, String phase, String lastError, Instant lastHeartbeatAt) {}

    private final AtomicReference<Snapshot> state =
            new AtomicReference<>(new Snapshot(DISCONNECTED, "STOPPED", null, null));

    public Snapshot snapshot() {
        return state.get();
    }

    public void markStarting() {
        state.updateAndGet(
                prev -> new Snapshot(DISCONNECTED, "STARTING", prev.lastError(), prev.lastHeartbeatAt()));
    }

    public void markUp() {
        state.updateAndGet(
                prev -> new Snapshot(CONNECTED, "UP", null, prev.lastHeartbeatAt()));
    }

    public void markLost(String error) {
        state.updateAndGet(
                prev ->
                        new Snapshot(
                                DISCONNECTED,
                                "BACKOFF",
                                error == null ? "session lost" : error,
                                prev.lastHeartbeatAt()));
    }

    public void markReconnectFailed(String error) {
        state.updateAndGet(
                prev ->
                        new Snapshot(
                                DISCONNECTED,
                                "BACKOFF",
                                error == null ? "reconnect failed" : error,
                                prev.lastHeartbeatAt()));
    }

    public void markStopped() {
        state.updateAndGet(
                prev -> new Snapshot(DISCONNECTED, "STOPPED", prev.lastError(), prev.lastHeartbeatAt()));
    }

    public void heartbeat(Instant at) {
        if (at == null) {
            return;
        }
        state.updateAndGet(
                prev -> new Snapshot(prev.status(), prev.phase(), prev.lastError(), at));
    }
}
