package com.factory.edge.opcua;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;

record ParsedTag(OpcUaProperties.TagConfig config, NodeId nodeId) {

    static List<ParsedTag> parseAll(List<OpcUaProperties.TagConfig> tags) {
        List<ParsedTag> parsed = new ArrayList<>(tags.size());
        for (OpcUaProperties.TagConfig tag : tags) {
            try {
                parsed.add(new ParsedTag(tag, NodeIdParser.parse(tag.getNodeId())));
            } catch (OpcUaProbeException e) {
                throw new OpcUaProbeException(
                        "Invalid NodeId for siteId="
                                + tag.getSiteId()
                                + " lineId="
                                + tag.getLineId()
                                + " tagCode="
                                + tag.getTagCode()
                                + " nodeId="
                                + tag.getNodeId()
                                + ": "
                                + e.getMessage(),
                        e);
            }
        }
        return parsed;
    }
}
