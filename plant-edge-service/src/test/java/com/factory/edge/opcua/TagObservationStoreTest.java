package com.factory.edge.opcua;

import static org.assertj.core.api.Assertions.assertThat;

import com.factory.edge.config.ActiveTagCatalog;
import java.time.Instant;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("EDGE-UI")
class TagObservationStoreTest {

    @Test
    void staleReceiveTimeDoesNotOverwriteNewerSample() {
        TagObservationStore store = new TagObservationStore();
        Instant newer = Instant.parse("2026-01-15T12:00:10Z");
        Instant older = Instant.parse("2026-01-15T12:00:00Z");

        store.recordAt(reading(50), true, newer);
        store.recordAt(reading(40), false, older);

        TagObservationStore.Observation obs = store.get("INDORE", "LINE-01", "VALVE_101_POS");
        assertThat(obs.value()).isEqualTo(50);
        assertThat(obs.emitted()).isTrue();
        assertThat(obs.observedAt()).isEqualTo(newer);
    }

    @Test
    void heldUpdateRefreshesObservedValue() {
        TagObservationStore store = new TagObservationStore();
        Instant t1 = Instant.parse("2026-01-15T12:00:00Z");
        Instant t2 = Instant.parse("2026-01-15T12:00:01Z");

        store.recordAt(reading(50.0), true, t1);
        store.recordAt(reading(50.2), false, t2);

        TagObservationStore.Observation obs = store.get("INDORE", "LINE-01", "VALVE_101_POS");
        assertThat(obs.value()).isEqualTo(50.2);
        assertThat(obs.emitted()).isFalse();
        assertThat(obs.observedAt()).isEqualTo(t2);
    }

    @Test
    void pruneKeepsOnlyAllowedKeysAndEmptySetClears() {
        TagObservationStore store = new TagObservationStore();
        Instant at = Instant.parse("2026-01-15T12:00:00Z");
        store.recordAt(reading(50), true, at);
        store.recordAt(readingFor("REACTOR_TEMP_C", 71.5), true, at);

        store.prune(java.util.List.of("INDORE|LINE-01|VALVE_101_POS"));

        assertThat(store.get("INDORE", "LINE-01", "VALVE_101_POS")).isNotNull();
        assertThat(store.get("INDORE", "LINE-01", "REACTOR_TEMP_C")).isNull();

        store.prune(java.util.List.of());
        assertThat(store.snapshot()).isEmpty();
    }

    @Test
    void publishedCatalogGatesOutTagsThatAreNoLongerActive() {
        ActiveTagCatalog catalog = new ActiveTagCatalog();
        TagObservationStore store = new TagObservationStore(catalog);
        Instant at = Instant.parse("2026-01-15T12:00:00Z");

        OpcUaProperties.TagConfig active = new OpcUaProperties.TagConfig();
        active.setSiteId("INDORE");
        active.setLineId("LINE-01");
        active.setTagCode("VALVE_101_POS");
        active.setNodeId("nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=1001");
        catalog.publish(java.util.List.of(active));

        store.recordAt(reading(50), true, at);
        store.recordAt(readingFor("REACTOR_TEMP_C", 71.5), true, at);

        assertThat(store.get("INDORE", "LINE-01", "VALVE_101_POS")).isNotNull();
        assertThat(store.get("INDORE", "LINE-01", "REACTOR_TEMP_C")).isNull();
    }

    @Test
    void emptyCatalogLeavesRecordingUngated() {
        TagObservationStore store = new TagObservationStore(new ActiveTagCatalog());
        store.recordAt(reading(50), true, Instant.parse("2026-01-15T12:00:00Z"));
        assertThat(store.get("INDORE", "LINE-01", "VALVE_101_POS")).isNotNull();
    }

    private static TagReading reading(Object value) {
        return readingFor("VALVE_101_POS", value);
    }

    private static TagReading readingFor(String tagCode, Object value) {
        return new TagReading(
                TagReading.EVENT_CHANGE,
                "INDORE",
                "LINE-01",
                tagCode,
                "nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=1001",
                value,
                "GOOD",
                Instant.parse("2026-01-15T12:00:00Z"),
                Instant.parse("2026-01-15T12:00:00Z"),
                null,
                null);
    }
}
