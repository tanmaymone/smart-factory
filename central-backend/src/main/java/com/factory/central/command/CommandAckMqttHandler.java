package com.factory.central.command;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.factory.central.mqtt.MqttInboundHandler;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CommandAckMqttHandler implements MqttInboundHandler {

    private static final Logger log = LoggerFactory.getLogger(CommandAckMqttHandler.class);
    private static final String ACK_TOPIC = "site/+/line/+/commands/acks";
    private static final Pattern TOPIC_PATTERN =
            Pattern.compile("^site/([^/]+)/line/([^/]+)/commands/acks$");

    private final CommandAckService commandAckService;
    private final ObjectMapper objectMapper;

    public CommandAckMqttHandler(CommandAckService commandAckService, ObjectMapper objectMapper) {
        this.commandAckService = commandAckService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String topicFilter() {
        return ACK_TOPIC;
    }

    @Override
    public void handle(String topic, MqttMessage message) {
        Matcher matcher = TOPIC_PATTERN.matcher(topic);
        if (!matcher.matches()) {
            log.debug("Ignoring MQTT topic {}", topic);
            return;
        }
        String siteId = matcher.group(1);
        String lineId = matcher.group(2);
        try {
            String raw = new String(message.getPayload(), StandardCharsets.UTF_8);
            JsonNode body = objectMapper.readTree(raw);
            UUID commandId = parseUuid(body.get("commandId"));
            String payloadSiteId = text(body, "siteId");
            String payloadLineId = text(body, "lineId");
            String status = text(body, "status");
            Object appliedValue = body.has("appliedValue") && !body.get("appliedValue").isNull()
                    ? objectMapper.convertValue(body.get("appliedValue"), Object.class)
                    : null;
            String errorMessage = text(body, "errorMessage");
            Instant executedAt = parseInstant(body.get("executedAt"));
            commandAckService.applyAck(
                    siteId,
                    lineId,
                    commandId,
                    payloadSiteId,
                    payloadLineId,
                    status,
                    appliedValue,
                    errorMessage,
                    executedAt);
        } catch (Exception ex) {
            log.warn("Failed to process MQTT command ACK topic={}: {}", topic, ex.getMessage());
        }
    }

    private static UUID parseUuid(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return UUID.fromString(node.asText());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String text(JsonNode body, String field) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asText();
    }

    private static Instant parseInstant(JsonNode node) {
        if (node == null || node.isNull() || node.asText().isBlank()) {
            return null;
        }
        try {
            return Instant.parse(node.asText());
        } catch (Exception ex) {
            return null;
        }
    }
}
