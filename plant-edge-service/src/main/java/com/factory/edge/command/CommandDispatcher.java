package com.factory.edge.command;

import com.factory.edge.opcua.OpcUaProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CommandDispatcher {

    private static final Logger log = LoggerFactory.getLogger(CommandDispatcher.class);
    private static final Pattern TOPIC =
            Pattern.compile("^site/([^/]+)/line/([^/]+)/commands$");

    private final ConfiguredTagIndex tags;
    private final CommandWriter writer;
    private final EdgeCommandHistory history;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final CommandAckMapper ackMapper;

    private AckSink ackSink = (topic, payload) -> false;

    @Autowired
    public CommandDispatcher(
            OpcUaProperties properties,
            CommandWriter writer,
            EdgeCommandHistory history,
            ObjectMapper objectMapper) {
        this(properties, writer, history, objectMapper, Clock.systemUTC());
    }

    CommandDispatcher(
            OpcUaProperties properties,
            CommandWriter writer,
            EdgeCommandHistory history,
            ObjectMapper objectMapper,
            Clock clock) {
        this.tags = new ConfiguredTagIndex(properties);
        this.writer = writer;
        this.history = history;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.ackMapper = new CommandAckMapper(objectMapper);
    }

    public void bindAckSink(AckSink sink) {
        this.ackSink = sink == null ? (t, p) -> false : sink;
    }

    public void handle(String topic, byte[] payload) {
        Matcher matcher = TOPIC.matcher(topic == null ? "" : topic);
        if (!matcher.matches()) {
            return;
        }
        String topicSite = matcher.group(1);
        String topicLine = matcher.group(2);
        if (!tags.isConfiguredLine(topicSite, topicLine)) {
            log.debug("Ignoring command for unconfigured site/line {}/{}", topicSite, topicLine);
            return;
        }

        JsonNode body;
        try {
            body = objectMapper.readTree(payload);
        } catch (Exception e) {
            log.warn("Ignoring invalid command JSON on {}: {}", topic, e.getMessage());
            return;
        }

        String commandId = text(body, "commandId");
        if (commandId == null || commandId.isBlank()) {
            log.warn("Ignoring command without commandId on {}", topic);
            return;
        }

        String payloadSite = text(body, "siteId");
        String payloadLine = text(body, "lineId");
        if (!topicSite.equals(payloadSite) || !topicLine.equals(payloadLine)) {
            log.warn(
                    "Dropping commandId={} topic={}/{} payload={}/{}",
                    commandId,
                    topicSite,
                    topicLine,
                    payloadSite,
                    payloadLine);
            return;
        }

        Optional<EdgeCommandHistory.Entry> prior = history.find(commandId);
        if (prior.isPresent() && prior.get().result().terminal()) {
            publishAck(prior.get().result());
            return;
        }

        CommandResult result = execute(topicSite, topicLine, commandId, body, clock.instant());
        boolean published = publishAck(result);
        history.record(result, published);
    }

    public void republishUnpublished() {
        for (CommandResult result : history.unpublishedTerminal()) {
            if (publishAck(result)) {
                history.markPublished(result.commandId());
            }
        }
    }

    private CommandResult execute(
            String siteId, String lineId, String commandId, JsonNode body, Instant now) {
        String tagCode = text(body, "tagCode");
        String protocol = text(body, "protocol");
        String dataType = text(body, "dataType");
        JsonNode targetValue = body.get("targetValue");
        String address = firstNonBlank(text(body, "nodeId"), text(body, "externalAddress"));

        if (protocol != null && !"OPC_UA".equalsIgnoreCase(protocol)) {
            return reject(commandId, siteId, lineId, tagCode, address, "unsupported protocol", now);
        }
        if (tagCode == null || tagCode.isBlank()) {
            return reject(commandId, siteId, lineId, null, address, "tagCode is required", now);
        }
        Optional<OpcUaProperties.TagConfig> mapped = tags.find(siteId, lineId, tagCode);
        if (mapped.isEmpty()) {
            return reject(commandId, siteId, lineId, tagCode, address, "unknown tag", now);
        }
        String resolvedNode = mapped.get().getNodeId();
        if (address != null && !address.isBlank() && !Objects.equals(address, resolvedNode)) {
            return reject(
                    commandId,
                    siteId,
                    lineId,
                    tagCode,
                    address,
                    "address does not match configured tag",
                    now);
        }
        if (dataType == null || dataType.isBlank()) {
            return reject(commandId, siteId, lineId, tagCode, resolvedNode, "dataType is required", now);
        }
        if (targetValue == null || targetValue.isNull()) {
            return reject(commandId, siteId, lineId, tagCode, resolvedNode, "targetValue is required", now);
        }
        return writer.write(commandId, siteId, lineId, tagCode, resolvedNode, dataType, targetValue, now);
    }

    private static CommandResult reject(
            String commandId,
            String siteId,
            String lineId,
            String tagCode,
            String nodeId,
            String reason,
            Instant now) {
        return CommandResult.rejected(
                commandId, siteId, lineId, tagCode, nodeId, null, reason, now, true);
    }

    private boolean publishAck(CommandResult result) {
        try {
            String topic = CommandAckMapper.topic(result.siteId(), result.lineId());
            boolean ok = ackSink.publish(topic, ackMapper.toJson(result));
            if (ok) {
                history.markPublished(result.commandId());
            }
            return ok;
        } catch (Exception e) {
            log.warn("ACK publish failed commandId={}: {}", result.commandId(), e.getMessage());
            return false;
        }
    }

    private static String text(JsonNode body, String field) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asText();
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b != null && !b.isBlank() ? b : null;
    }

    @FunctionalInterface
    public interface AckSink {
        boolean publish(String topic, String payload);
    }
}
