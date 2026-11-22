package com.factory.edge.command;

import com.factory.edge.opcua.OpcUaProperties;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Edge yaml tag allow-list keyed by site|line|tagCode. */
final class ConfiguredTagIndex {

    private final Set<String> configuredLines = new HashSet<>();
    private final Map<String, OpcUaProperties.TagConfig> tagsByKey = new HashMap<>();

    ConfiguredTagIndex(OpcUaProperties properties) {
        for (OpcUaProperties.TagConfig tag : properties.getTags()) {
            configuredLines.add(lineKey(tag.getSiteId(), tag.getLineId()));
            tagsByKey.put(tagKey(tag.getSiteId(), tag.getLineId(), tag.getTagCode()), tag);
        }
    }

    boolean isConfiguredLine(String siteId, String lineId) {
        return configuredLines.contains(lineKey(siteId, lineId));
    }

    Optional<OpcUaProperties.TagConfig> find(String siteId, String lineId, String tagCode) {
        return Optional.ofNullable(tagsByKey.get(tagKey(siteId, lineId, tagCode)));
    }

    private static String lineKey(String siteId, String lineId) {
        return siteId + "|" + lineId;
    }

    private static String tagKey(String siteId, String lineId, String tagCode) {
        return siteId + "|" + lineId + "|" + tagCode;
    }
}
