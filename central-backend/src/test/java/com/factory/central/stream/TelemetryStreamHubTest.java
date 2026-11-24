package com.factory.central.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.factory.central.cache.AlarmDirection;
import com.factory.central.cache.AlarmSeverity;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.factory.central.cache.InMemoryLatestTagCache;
import com.factory.central.cache.LatestTagState;
import com.factory.central.ingestion.LatestTagStateResponse;
import com.factory.central.registry.RegistryLivenessService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("SF-M1.5")
class TelemetryStreamHubTest {

    private InMemoryLatestTagCache cache;
    private SimpleMeterRegistry meters;
    private StreamProperties properties;
    private TelemetryStreamHub hub;

    @BeforeEach
    void setUp() {
        cache = new InMemoryLatestTagCache();
        meters = new SimpleMeterRegistry();
        properties = new StreamProperties();
        properties.setDispatchPoolSize(2);
        properties.setDispatchQueueCapacity(64);
        properties.setMaxSubscribers(32);
        hub = new TelemetryStreamHub(properties, cache, passthroughLiveness(), meters);
    }

    private static RegistryLivenessService passthroughLiveness() {
        RegistryLivenessService liveness = mock(RegistryLivenessService.class);
        when(liveness.filterLive(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        return liveness;
    }

    @AfterEach
    void tearDown() {
        hub.shutdown();
    }

    @Test
    void filtersBySiteAndLineAndDeliversCommandStatusConstants() {
        RecordingStreamSession indore = new RecordingStreamSession();
        RecordingStreamSession pune = new RecordingStreamSession();
        hub.registerSession(indore, "INDORE", "LINE-01");
        hub.registerSession(pune, "PUNE", "LINE-01");

        awaitConnected(indore);
        awaitConnected(pune);

        LatestTagState tag = state("INDORE", "LINE-01", "REACTOR_TEMP_C", 85.0, AlarmSeverity.CRITICAL);
        hub.publishTag(tag);
        hub.publishAlarm(tag);
        hub.publishCommand(command(CommandUpdateEvent.Status.VALIDATED_DISPATCHED));

        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            assertThat(indore.eventsNamed(TelemetryStreamHub.EVENT_TAG_UPDATE)).isNotEmpty();
            assertThat(indore.eventsNamed(TelemetryStreamHub.EVENT_ALARM)).isNotEmpty();
            assertThat(indore.eventsNamed(TelemetryStreamHub.EVENT_COMMAND))
                    .extracting(e -> ((CommandUpdateEvent) e.data()).status())
                    .containsExactly(CommandUpdateEvent.Status.VALIDATED_DISPATCHED);
            assertThat(pune.eventsNamed(TelemetryStreamHub.EVENT_TAG_UPDATE)).isEmpty();
            assertThat(pune.eventsNamed(TelemetryStreamHub.EVENT_COMMAND)).isEmpty();
        });

        hub.publishCommand(command(CommandUpdateEvent.Status.ACKED));
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> assertThat(indore.eventsNamed(
                        TelemetryStreamHub.EVENT_COMMAND))
                .extracting(e -> ((CommandUpdateEvent) e.data()).status())
                .containsExactly(
                        CommandUpdateEvent.Status.VALIDATED_DISPATCHED, CommandUpdateEvent.Status.ACKED));
    }

    @Test
    void snapshotAlarmsAreNonNoneOnlyAndPayloadMatchesLatestTagStateResponse() {
        cache.put(state("INDORE", "LINE-01", "REACTOR_TEMP_C", 85.0, AlarmSeverity.CRITICAL));
        cache.put(state("INDORE", "LINE-01", "VALVE_101_POS", 10.0, AlarmSeverity.NONE));

        RecordingStreamSession session = new RecordingStreamSession();
        hub.registerSession(session, "INDORE", "LINE-01");

        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            assertThat(session.comments()).contains("connected");
            assertThat(session.eventsNamed(TelemetryStreamHub.EVENT_TAG_UPDATE)).hasSize(2);
            List<RecordingStreamSession.SentEvent> alarms = session.eventsNamed(TelemetryStreamHub.EVENT_ALARM);
            assertThat(alarms).hasSize(1);
            assertThat(alarms.getFirst().data()).isInstanceOf(LatestTagStateResponse.class);
            LatestTagStateResponse alarm = (LatestTagStateResponse) alarms.getFirst().data();
            assertThat(alarm.severity()).isEqualTo(AlarmSeverity.CRITICAL);
            assertThat(alarm.tagCode()).isEqualTo("REACTOR_TEMP_C");
        });
    }

    @Test
    void publishCommandRejectsNullBlankAndUnknownStatus() {
        RecordingStreamSession session = new RecordingStreamSession();
        hub.registerSession(session, null, null);
        awaitConnected(session);
        int before = hub.subscriberCount();

        assertThatThrownBy(() -> hub.publishCommand(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> hub.publishCommand(command(" "))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> hub.publishCommand(command("NOT_A_STATUS")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(hub.subscriberCount()).isEqualTo(before);
        assertThat(session.eventsNamed(TelemetryStreamHub.EVENT_COMMAND)).isEmpty();
    }

    @Test
    void sendFailureRemovesSubscriber() {
        RecordingStreamSession session = new RecordingStreamSession();
        hub.registerSession(session, "INDORE", "LINE-01");
        awaitConnected(session);
        session.failNextSend(new IOException("client gone"));

        hub.publishTag(state("INDORE", "LINE-01", "REACTOR_TEMP_C", 1.0, AlarmSeverity.NONE));

        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            assertThat(hub.subscriberCount()).isZero();
            assertThat(session.completeCount()).isGreaterThanOrEqualTo(1);
        });
    }

    @Test
    void publishReturnsWhileSendBlockedAndCoalescesLatest() throws Exception {
        properties.setDispatchPoolSize(1);
        properties.setDispatchQueueCapacity(8);
        hub.shutdown();
        hub = new TelemetryStreamHub(properties, cache, passthroughLiveness(), meters);

        RecordingStreamSession session = new RecordingStreamSession();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        session.blockSends(entered, release);
        hub.registerSession(session, "INDORE", "LINE-01");

        assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();

        long start = System.nanoTime();
        hub.publishTag(state("INDORE", "LINE-01", "REACTOR_TEMP_C", 1.0, AlarmSeverity.NONE));
        hub.publishTag(state("INDORE", "LINE-01", "REACTOR_TEMP_C", 2.0, AlarmSeverity.NONE));
        hub.publishTag(state("INDORE", "LINE-01", "REACTOR_TEMP_C", 3.0, AlarmSeverity.NONE));
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertThat(elapsedMs).isLessThan(500);

        release.countDown();

        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            List<RecordingStreamSession.SentEvent> tags = session.eventsNamed(TelemetryStreamHub.EVENT_TAG_UPDATE);
            assertThat(tags).isNotEmpty();
            LatestTagStateResponse last = (LatestTagStateResponse) tags.getLast().data();
            assertThat(last.value()).isEqualTo(3.0);
        });
    }

    @Test
    void fullDispatchQueueIncrementsDropCounterWithoutThrowing() throws Exception {
        properties.setDispatchPoolSize(1);
        properties.setDispatchQueueCapacity(1);
        hub.shutdown();
        hub = new TelemetryStreamHub(properties, cache, passthroughLiveness(), meters);

        RecordingStreamSession session = new RecordingStreamSession();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        session.blockSends(entered, release);
        hub.registerSession(session, "INDORE", "LINE-01");
        assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();

        for (int i = 0; i < 50; i++) {
            hub.publishTag(state("INDORE", "LINE-01", "REACTOR_TEMP_C", (double) i, AlarmSeverity.NONE));
        }

        assertThat(meters.counter("stream.events.dropped").count()).isGreaterThan(0);
        release.countDown();
    }

    @Test
    void thirtyThirdSubscribeThrowsAndLeavesListUnchanged() {
        properties.setMaxSubscribers(2);
        hub.shutdown();
        hub = new TelemetryStreamHub(properties, cache, passthroughLiveness(), meters);

        RecordingStreamSession first = new RecordingStreamSession();
        RecordingStreamSession second = new RecordingStreamSession();
        hub.registerSession(first, null, null);
        hub.registerSession(second, null, null);
        assertThat(hub.subscriberCount()).isEqualTo(2);

        RecordingStreamSession rejected = new RecordingStreamSession();
        assertThatThrownBy(() -> hub.registerSession(rejected, null, null))
                .isInstanceOf(StreamAtCapacityException.class);
        assertThat(hub.subscriberCount()).isEqualTo(2);
        assertThat(rejected.comments()).isEmpty();
        assertThat(rejected.completeCount()).isZero();
    }

    @Test
    void concurrentSubscribesNeverExceedMax() throws Exception {
        properties.setMaxSubscribers(8);
        hub.shutdown();
        hub = new TelemetryStreamHub(properties, cache, passthroughLiveness(), meters);

        ExecutorService pool = Executors.newFixedThreadPool(16);
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            futures.add(pool.submit(() -> {
                try {
                    hub.registerSession(new RecordingStreamSession(), null, null);
                    accepted.incrementAndGet();
                } catch (StreamAtCapacityException ex) {
                    rejected.incrementAndGet();
                }
            }));
        }
        for (Future<?> future : futures) {
            future.get(5, TimeUnit.SECONDS);
        }
        pool.shutdownNow();

        assertThat(hub.subscriberCount()).isEqualTo(8);
        assertThat(accepted.get()).isEqualTo(8);
        assertThat(rejected.get()).isEqualTo(32);
    }

    private void awaitConnected(RecordingStreamSession session) {
        await().atMost(Duration.ofSeconds(3)).until(() -> session.comments().contains("connected"));
    }

    private static LatestTagState state(
            String siteId, String lineId, String tagCode, double value, AlarmSeverity severity) {
        return LatestTagState.initial(
                siteId,
                lineId,
                tagCode,
                value,
                "GOOD",
                Instant.parse("2026-11-19T12:00:00Z"),
                severity,
                severity == AlarmSeverity.NONE ? null : AlarmDirection.HIGH);
    }

    private static CommandUpdateEvent command(String status) {
        return new CommandUpdateEvent(
                UUID.randomUUID().toString(),
                "INDORE",
                "LINE-01",
                "VALVE_101_POS",
                status,
                50.0,
                null,
                Instant.parse("2026-11-19T12:05:00Z"));
    }
}
