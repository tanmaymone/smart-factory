package com.factory.edge.command;

import com.factory.edge.opcua.OpcUaProperties;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Immutable edge tag allow-list keyed by site|line|tagCode. Replaced wholesale on config apply. */
final class ConfiguredTagIndex {

    private final Set<String> configuredLines = new HashSet<>();
    private final Map<String, OpcUaProperties.TagConfig> tagsByKey = new HashMap<>();

    ConfiguredTagIndex(OpcUaProperties properties) {
        this(properties.getTags());
    }

    ConfiguredTagIndex(List<OpcUaProperties.TagConfig> tags) {
        if (tags == null) {
            return;
        }
        for (OpcUaProperties.TagConfig tag : tags) {
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
