package com.factory.edge.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Section 7.3 ACK JSON with explicit nulls for unused fields. */
final class CommandAckMapper {

    private final ObjectMapper mapper;

    CommandAckMapper(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    String toJson(CommandResult result) {
        try {
            ObjectNode root = mapper.createObjectNode();
            root.put("commandId", result.commandId());
            root.put("siteId", result.siteId());
            root.put("lineId", result.lineId());
            root.put("tagCode", result.tagCode());
            root.put("status", result.status());
            putValueOrNull(root, "appliedValue", result.observedValue());
            putValueOrNull(root, "observedValue", result.observedValue());
            if (result.reason() == null) {
                root.putNull("errorMessage");
                root.putNull("reason");
            } else {
                root.put("errorMessage", result.reason());
                root.put("reason", result.reason());
            }
            root.put("executedAt", result.executedAt().toString());
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encode command ACK", e);
        }
    }

    static String topic(String siteId, String lineId) {
        return "site/" + siteId + "/line/" + lineId + "/commands/acks";
    }

    private static void putValueOrNull(ObjectNode root, String field, Object value) {
        if (value == null) {
            root.putNull(field);
        } else if (value instanceof Number n) {
            if (value instanceof Double || value instanceof Float) {
                root.put(field, n.doubleValue());
            } else {
                root.put(field, n.longValue());
            }
        } else if (value instanceof Boolean b) {
            root.put(field, b);
        } else if (value instanceof String s) {
            root.put(field, s);
        } else {
            root.put(field, String.valueOf(value));
        }
    }
}
