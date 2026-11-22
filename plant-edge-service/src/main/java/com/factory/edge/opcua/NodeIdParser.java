package com.factory.edge.opcua;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;

/**
 * Parses only {@code ns=<index>;i=<numeric>} and {@code ns=<index>;s=<string>} forms used by the
 * M1 seed / Prosys Simulation nodes.
 */
public final class NodeIdParser {

    private static final Pattern NUMERIC =
            Pattern.compile("^ns=(\\d+);i=(-?\\d+)$");
    private static final Pattern STRING =
            Pattern.compile("^ns=(\\d+);s=(.+)$");

    private NodeIdParser() {}

    public static NodeId parse(String text) {
        if (text == null || text.isBlank()) {
            throw new OpcUaProbeException("NodeId text is blank");
        }
        String trimmed = text.trim();
        Matcher numeric = NUMERIC.matcher(trimmed);
        if (numeric.matches()) {
            return new NodeId(Integer.parseInt(numeric.group(1)), Integer.parseInt(numeric.group(2)));
        }
        Matcher string = STRING.matcher(trimmed);
        if (string.matches()) {
            return new NodeId(Integer.parseInt(string.group(1)), string.group(2));
        }
        throw new OpcUaProbeException(
                "Invalid NodeId syntax (expected ns=<index>;i=<numeric> or ns=<index>;s=<string>): "
                        + trimmed);
    }
}
