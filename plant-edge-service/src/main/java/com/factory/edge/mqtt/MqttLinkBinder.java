package com.factory.edge.mqtt;

import com.factory.edge.buffer.StoreAndForwardQueue;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/** Wires live queue depth into {@link MqttTelemetryLink} after both beans exist. */
@Component
class MqttLinkBinder {

    private final MqttTelemetryLink link;
    private final StoreAndForwardQueue queue;

    MqttLinkBinder(MqttTelemetryLink link, StoreAndForwardQueue queue) {
        this.link = link;
        this.queue = queue;
    }

    @PostConstruct
    void bind() {
        link.bindBufferedCount(queue::depth);
    }
}
