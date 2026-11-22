package com.factory.edge.buffer;

/**
 * Outbound telemetry transport. Eclipse Paho binds this port; recovery callbacks kick the paced
 * SQLite drain without publishing on the MQTT callback thread.
 */
public interface TelemetryLink {

    void publish(TelemetryRecord record) throws LinkUnavailableException;

    boolean isAvailable();

    /** Register the drain kick invoked on broker connect/reconnect. Default is a no-op. */
    default void bindRecovery(Runnable onRecovered) {}
}
