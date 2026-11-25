package com.factory.edge.config;

import com.factory.edge.opcua.OpcUaProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

/** Parsed central manifest. {@code rawJson} is the exact HTTP body (never re-serialized). */
public record EdgeManifest(
        String siteId,
        String siteLifecycle,
        long version,
        String contentHash,
        List<OpcUaProperties.TagConfig> tags,
        String rawJson) {

    public static EdgeManifest parse(String rawJson, JsonNode root) {
        String siteId = text(root, "siteId");
        String lifecycle = text(root, "siteLifecycle");
        long version = root.path("version").asLong(0L);
        String contentHash = text(root, "contentHash");
        List<OpcUaProperties.TagConfig> tags = new ArrayList<>();
        JsonNode lines = root.path("lines");
        if (lines.isArray()) {
            for (JsonNode line : lines) {
                String lineId = text(line, "lineId");
                JsonNode tagNodes = line.path("tags");
                if (!tagNodes.isArray()) {
                    continue;
                }
                for (JsonNode tag : tagNodes) {
                    OpcUaProperties.TagConfig cfg = new OpcUaProperties.TagConfig();
                    cfg.setSiteId(siteId);
                    cfg.setLineId(lineId);
                    cfg.setTagCode(text(tag, "tagCode"));
                    cfg.setNodeId(text(tag, "externalAddress"));
                    cfg.setDeadband(tag.path("deadband").asDouble(0.0));
                    // Fail closed: an absent flag must not hand the command path a writable tag.
                    cfg.setWritable(tag.path("writable").asBoolean(false));
                    cfg.setProtocol(text(tag, "protocol"));
                    cfg.setDataType(text(tag, "dataType"));
                    cfg.setEndpointUrl(text(tag, "endpointUrl"));
                    tags.add(cfg);
                }
            }
        }
        return new EdgeManifest(siteId, lifecycle, version, contentHash, List.copyOf(tags), rawJson);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText();
    }
}
