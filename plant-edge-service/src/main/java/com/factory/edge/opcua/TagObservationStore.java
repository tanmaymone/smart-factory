package com.factory.edge.opcua;

import com.factory.edge.filter.DeadbandFilter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
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
        String key = DeadbandFilter.key(reading.siteId(), reading.lineId(), reading.tagCode());
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

    public Observation get(String siteId, String lineId, String tagCode) {
        return byKey.get(DeadbandFilter.key(siteId, lineId, tagCode));
    }

    public List<Observation> snapshot() {
        return List.copyOf(new ArrayList<>(byKey.values()));
    }
}
