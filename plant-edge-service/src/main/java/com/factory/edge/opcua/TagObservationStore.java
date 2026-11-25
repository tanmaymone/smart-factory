package com.factory.edge.opcua;

import com.factory.edge.config.ActiveTagCatalog;
import com.factory.edge.filter.DeadbandFilter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Latest OPC UA sample per tag, including deadband-held readings. */
@Component
public class TagObservationStore {

    public record Observation(
            String siteId,
            String lineId,
            String tagCode,
            String nodeId,
            Object value,
            String quality,
            Instant observedAt,
            boolean emitted) {}

    private final ConcurrentHashMap<String, Observation> byKey = new ConcurrentHashMap<>();
    private final ActiveTagCatalog catalog;

    /** Leaf lock: held only around map mutation, never while calling out. */
    private final Object writeLock = new Object();

    @Autowired
    public TagObservationStore(ActiveTagCatalog catalog) {
        this.catalog = catalog;
    }

    /** Ungated store for unit tests and the standalone diagnostics sink. */
    public TagObservationStore() {
        this(new ActiveTagCatalog());
    }

    public void record(TagReading reading, boolean emitted) {
        if (reading == null) {
            return;
        }
        recordAt(reading, emitted, Instant.now());
    }

    /** Test helper: record with an explicit receive time for stale-overwrite checks. */
    void recordAt(TagReading reading, boolean emitted, Instant receiveAt) {
        if (reading == null || receiveAt == null) {
            return;
        }
        // Once central config is published, a reading for a tag outside the catalog is stale.
        if (catalog.size() > 0
                && !catalog.contains(reading.siteId(), reading.lineId(), reading.tagCode())) {
            return;
        }
        String key = DeadbandFilter.key(reading.siteId(), reading.lineId(), reading.tagCode());
        synchronized (writeLock) {
            byKey.compute(
                    key,
                    (k, prev) -> {
                        if (prev != null
                                && prev.observedAt() != null
                                && receiveAt.isBefore(prev.observedAt())) {
                            return prev;
                        }
                        return new Observation(
                                reading.siteId(),
                                reading.lineId(),
                                reading.tagCode(),
                                reading.nodeId(),
                                reading.value(),
                                reading.quality(),
                                receiveAt,
                                emitted);
                    });
        }
    }

    /** Keeps only the given {@code siteId|lineId|tagCode} keys; an empty set clears the store. */
    public void prune(Collection<String> allowedKeys) {
        Set<String> allowed = allowedKeys == null ? Set.of() : new HashSet<>(allowedKeys);
        synchronized (writeLock) {
            byKey.keySet().removeIf(key -> !allowed.contains(key));
        }
    }

    public void clearAll() {
        synchronized (writeLock) {
            byKey.clear();
        }
    }

    public Observation get(String siteId, String lineId, String tagCode) {
        return byKey.get(DeadbandFilter.key(siteId, lineId, tagCode));
    }

    public List<Observation> snapshot() {
        return List.copyOf(new ArrayList<>(byKey.values()));
    }
}
