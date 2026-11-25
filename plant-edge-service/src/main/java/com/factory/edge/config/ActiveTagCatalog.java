package com.factory.edge.config;

import com.factory.edge.opcua.OpcUaProperties;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/** Published accepted-tag set for observation gating and status. */
@Component
public class ActiveTagCatalog {

    public record TagKey(String siteId, String lineId, String tagCode) {}

    public record Entry(OpcUaProperties.TagConfig tag, String externalAddress) {}

    private final AtomicReference<Map<TagKey, Entry>> entries =
            new AtomicReference<>(Map.of());

    public void publish(List<OpcUaProperties.TagConfig> tags) {
        if (tags == null || tags.isEmpty()) {
            entries.set(Map.of());
            return;
        }
        // Manifest order so the diagnostics tag table stays stable between polls.
        Map<TagKey, Entry> next = new LinkedHashMap<>();
        for (OpcUaProperties.TagConfig tag : tags) {
            TagKey key = new TagKey(tag.getSiteId(), tag.getLineId(), tag.getTagCode());
            next.put(key, new Entry(tag, tag.getNodeId()));
        }
        entries.set(Collections.unmodifiableMap(next));
    }

    public void clear() {
        entries.set(Map.of());
    }

    public boolean contains(String siteId, String lineId, String tagCode) {
        return entries.get().containsKey(new TagKey(siteId, lineId, tagCode));
    }

    public Entry get(String siteId, String lineId, String tagCode) {
        return entries.get().get(new TagKey(siteId, lineId, tagCode));
    }

    public Map<TagKey, Entry> snapshot() {
        return entries.get();
    }

    public List<OpcUaProperties.TagConfig> tags() {
        return entries.get().values().stream().map(Entry::tag).toList();
    }

    public int size() {
        return entries.get().size();
    }
}
