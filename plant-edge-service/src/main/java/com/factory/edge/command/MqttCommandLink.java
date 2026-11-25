package com.factory.edge.command;

import com.factory.edge.config.EdgeConfigProperties;
import com.factory.edge.mqtt.MqttProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Edge command intake: queue + single writer thread over {@link MqttCommandClient}. cleanSession
 * resubscribe lives in the client.
 */
@Component
public class MqttCommandLink {

    private static final Logger log = LoggerFactory.getLogger(MqttCommandLink.class);
    private static final int QUEUE_CAPACITY = 32;

    private final CommandDispatcher dispatcher;
    private final EdgeCommandHistory history;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final CommandAckMapper ackMapper;
    private final MqttCommandClient client;
    private final BlockingQueue<QueuedCommand> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Thread workerThread;

    @Autowired
    public MqttCommandLink(
            MqttProperties properties,
            EdgeConfigProperties edgeConfig,
            CommandDispatcher dispatcher,
            EdgeCommandHistory history,
            ObjectMapper objectMapper)
            throws MqttException {
        this(properties, edgeConfig, dispatcher, history, objectMapper, Clock.systemUTC());
    }

    MqttCommandLink(
            MqttProperties properties,
            EdgeConfigProperties edgeConfig,
            CommandDispatcher dispatcher,
            EdgeCommandHistory history,
            ObjectMapper objectMapper,
            Clock clock)
            throws MqttException {
        this.dispatcher = dispatcher;
        this.history = history;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.ackMapper = new CommandAckMapper(objectMapper);
        this.client = new MqttCommandClient(properties, commandFilter(edgeConfig));
        this.dispatcher.bindAckSink(client::publish);
        this.workerThread = new Thread(this::workerLoop, "edge-command");
        this.workerThread.setDaemon(true);
        this.client.start(reconnect -> dispatcher.republishUnpublished(), this::enqueue);
        this.workerThread.start();
    }

    public boolean isConnected() {
        return client.isConnected();
    }

    /** One site per edge process once config sync owns the tag list (M4.1 ACLs build on this). */
    static String commandFilter(EdgeConfigProperties edgeConfig) {
        if (edgeConfig != null && edgeConfig.isConfigSyncEnabled()) {
            return "site/" + edgeConfig.getNormalizedSiteId() + "/line/+/commands";
        }
        return "site/+/line/+/commands";
    }

    private void enqueue(String topic, byte[] payload) {
        String commandId = peekCommandId(payload);
        if (commandId != null && !inFlight.add(commandId)) {
            return;
        }
        if (!queue.offer(new QueuedCommand(topic, payload, commandId))) {
            if (commandId != null) {
                inFlight.remove(commandId);
            }
            rejectQueueFull(topic, payload);
        }
    }

    private void workerLoop() {
        while (!closed.get()) {
            try {
                QueuedCommand cmd = queue.take();
                try {
                    dispatcher.handle(cmd.topic(), cmd.payload());
                } finally {
                    if (cmd.commandId() != null) {
                        inFlight.remove(cmd.commandId());
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.warn("EDGE CMD worker error: {}", e.getMessage());
            }
        }
    }

    private void rejectQueueFull(String topic, byte[] payload) {
        try {
            JsonNode body = objectMapper.readTree(payload);
            String commandId = text(body, "commandId");
            String siteId = text(body, "siteId");
            String lineId = text(body, "lineId");
            if (commandId == null || siteId == null || lineId == null) {
                return;
            }
            CommandResult result =
                    CommandResult.rejected(
                            commandId,
                            siteId,
                            lineId,
                            text(body, "tagCode"),
                            null,
                            null,
                            "command queue full",
                            clock.instant(),
                            false);
            client.publish(CommandAckMapper.topic(siteId, lineId), ackMapper.toJson(result));
            history.record(result, false);
        } catch (Exception e) {
            log.warn("Failed to reject queue-full command on {}: {}", topic, e.getMessage());
        }
    }

    private String peekCommandId(byte[] payload) {
        try {
            return text(objectMapper.readTree(payload), "commandId");
        } catch (Exception e) {
            return null;
        }
    }

    private static String text(JsonNode body, String field) {
        JsonNode node = body.get(field);
        return node == null || node.isNull() ? null : node.asText();
    }

    @PreDestroy
    void shutdown() {
        closed.set(true);
        workerThread.interrupt();
        client.shutdown();
    }

    private record QueuedCommand(String topic, byte[] payload, String commandId) {}
}
