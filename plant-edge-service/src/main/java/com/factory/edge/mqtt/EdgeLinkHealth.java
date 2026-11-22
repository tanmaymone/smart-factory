package com.factory.edge.mqtt;

import java.time.Instant;

/** Local MQTT link health for edge diagnostics (not the Section 7.4 heartbeat topic). */
public record EdgeLinkHealth(String status, int bufferedCount, Instant lastPublishAt) {

    public static final String CONNECTED = "CONNECTED";
    public static final String DISCONNECTED = "DISCONNECTED";
}
