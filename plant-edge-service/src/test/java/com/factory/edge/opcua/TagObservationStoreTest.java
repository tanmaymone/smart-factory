package com.factory.edge.opcua;

import static org.assertj.core.api.Assertions.assertThat;

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

    private static TagReading reading(Object value) {
        return new TagReading(
                TagReading.EVENT_CHANGE,
                "INDORE",
                "LINE-01",
                "VALVE_101_POS",
                "ns=3;i=1001",
                value,
                "GOOD",
                Instant.parse("2026-01-15T12:00:00Z"),
                Instant.parse("2026-01-15T12:00:00Z"),
                null);
    }
}
