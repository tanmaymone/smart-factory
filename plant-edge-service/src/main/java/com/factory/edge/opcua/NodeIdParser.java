package com.factory.edge.opcua;

import java.util.Base64;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;

/**
 * Parses plant addresses as {@code nsu=<uri>;(i|s|g|b)=<id>} or {@code ns=0;...} built-ins. Non-zero
 * {@code ns=} indexes are rejected — resolve the URI against Server_NamespaceArray instead.
 */
public final class NodeIdParser {

    private static final Pattern NS0 =
            Pattern.compile("^ns=0;(i=(-?\\d+)|s=(.+)|g=([0-9a-fA-F\\-]{36})|b=([A-Za-z0-9+/=]+))$");
    private static final Pattern NS_INDEX = Pattern.compile("^ns=(\\d+);");
    private static final Pattern NSU_TAIL = Pattern.compile(";([isgb])=(.*)$", Pattern.DOTALL);

    private NodeIdParser() {}

    public static NodeAddress parse(String text) {
        if (text == null || text.isBlank()) {
            throw new OpcUaProbeException("NodeId text is blank");
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("nsu=")) {
            return parseNsu(trimmed);
        }
        Matcher ns0 = NS0.matcher(trimmed);
        if (ns0.matches()) {
            return new NodeAddress.Resolved(parseNs0Identifier(ns0));
        }
        Matcher indexed = NS_INDEX.matcher(trimmed);
        if (indexed.find()) {
            int index = Integer.parseInt(indexed.group(1));
            if (index != 0) {
                throw new OpcUaProbeException(
                        "Non-zero ns= index is not a plant address (use nsu=<uri>;…): " + trimmed);
            }
        }
        throw new OpcUaProbeException(
                "Invalid NodeId syntax (expected nsu=<uri>;(i|s|g|b)=<id> or ns=0;…): " + trimmed);
    }

    private static NodeAddress.Unresolved parseNsu(String trimmed) {
        String afterPrefix = trimmed.substring(4);
        Matcher tail = NSU_TAIL.matcher(afterPrefix);
        if (!tail.find()) {
            throw new OpcUaProbeException(
                    "Invalid NodeId syntax (expected nsu=<uri>;(i|s|g|b)=<id>): " + trimmed);
        }
        String uri = afterPrefix.substring(0, tail.start());
        if (uri.isBlank() || uri.indexOf(';') >= 0) {
            throw new OpcUaProbeException(
                    "Invalid NodeId syntax (namespace URI must be non-blank and must not contain ';'): "
                            + trimmed);
        }
        String kind = tail.group(1);
        String idText = tail.group(2);
        return new NodeAddress.Unresolved(uri, parseIdentifier(kind, idText, trimmed));
    }

    private static NodeId parseNs0Identifier(Matcher ns0) {
        if (ns0.group(2) != null) {
            return new NodeId(0, Integer.parseInt(ns0.group(2)));
        }
        if (ns0.group(3) != null) {
            return new NodeId(0, ns0.group(3));
        }
        if (ns0.group(4) != null) {
            return new NodeId(0, UUID.fromString(ns0.group(4)));
        }
        return new NodeId(0, ByteString.of(Base64.getDecoder().decode(ns0.group(5))));
    }

    private static NodeAddress.Identifier parseIdentifier(String kind, String idText, String whole) {
        try {
            return switch (kind) {
                case "i" -> new NodeAddress.NumericId(Integer.parseInt(idText));
                case "s" -> {
                    if (idText.isEmpty()) {
                        throw new OpcUaProbeException("Invalid NodeId syntax (empty string id): " + whole);
                    }
                    yield new NodeAddress.StringId(idText);
                }
                case "g" -> new NodeAddress.GuidId(UUID.fromString(idText));
                case "b" -> new NodeAddress.OpaqueId(ByteString.of(Base64.getDecoder().decode(idText)));
                default -> throw new OpcUaProbeException("Invalid NodeId syntax: " + whole);
            };
        } catch (IllegalArgumentException e) {
            throw new OpcUaProbeException("Invalid NodeId identifier in " + whole + ": " + e.getMessage(), e);
        }
    }
}
