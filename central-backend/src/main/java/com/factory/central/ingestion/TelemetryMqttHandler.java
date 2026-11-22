package com.factory.central.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.factory.central.mqtt.MqttInboundHandler;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class TelemetryMqttHandler implements MqttInboundHandler {

    private static final Logger log = LoggerFactory.getLogger(TelemetryMqttHandler.class);
    private static final String TELEMETRY_TOPIC = "site/+/line/+/telemetry";
    private static final Pattern TOPIC_PATTERN =
            Pattern.compile("^site/([^/]+)/line/([^/]+)/telemetry$");

    private final TelemetryIngestService ingestService;
    private final ObjectMapper objectMapper;

    public TelemetryMqttHandler(TelemetryIngestService ingestService, ObjectMapper objectMapper) {
        this.ingestService = ingestService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String topicFilter() {
        return TELEMETRY_TOPIC;
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
            String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
            JsonNode body = objectMapper.readTree(payload);
            IngestResult result = ingestService.ingestMqtt(body, siteId, lineId);
            if (result.bufferFull()) {
                log.warn(
                        "MQTT ingest buffer full site={} line={} accepted={} errors={}",
                        siteId,
                        lineId,
                        result.accepted(),
                        result.errors().size());
            } else if (result.accepted() == 0) {
                log.debug(
                        "MQTT ingest rejected all readings site={} line={} errors={}",
                        siteId,
                        lineId,
                        result.errors());
            }
        } catch (Exception ex) {
            log.warn("Failed to process MQTT telemetry topic={}: {}", topic, ex.getMessage());
        }
    }
}
