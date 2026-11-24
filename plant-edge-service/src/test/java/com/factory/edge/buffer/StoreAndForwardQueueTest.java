package com.factory.edge.buffer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@Tag("EDGE-BUFFER")
@ExtendWith(OutputCaptureExtension.class)
class StoreAndForwardQueueTest {

    @TempDir
    Path tempDir;

    @Test
    void disconnectBuffersThenOrderedReplay() {
        ControllableLink link = new ControllableLink();
        link.setAvailable(false);
        try (StoreAndForwardQueue queue = newQueue(link, 100)) {
            queue.offer(record("A", 1));
            queue.offer(record("B", 2));
            queue.offer(record("C", 3));
            assertThat(queue.depth()).isEqualTo(3);
            assertThat(link.published).isEmpty();

            link.setAvailable(true);
            queue.onLinkRecovered();

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                assertThat(link.published.stream().map(TelemetryRecord::tagCode).toList())
                        .containsExactly("A", "B", "C");
                assertThat(queue.depth()).isZero();
            });
        }
    }

    @Test
    void midDrainFailureResumesAtFailedRow() {
        ControllableLink link = new ControllableLink();
        link.setAvailable(false);
        try (StoreAndForwardQueue queue = newQueue(link, 100)) {
            queue.offer(record("A", 1));
            queue.offer(record("B", 2));
            queue.offer(record("C", 3));

            link.setAvailable(true);
            link.failOnTagCodes.add("B");
            queue.onLinkRecovered();

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                assertThat(link.published.stream().map(TelemetryRecord::tagCode).toList())
                        .containsExactly("A");
                assertThat(queue.depth()).isEqualTo(2);
            });

            link.failOnTagCodes.clear();
            queue.onLinkRecovered();

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                assertThat(link.published.stream().map(TelemetryRecord::tagCode).toList())
                        .containsExactly("A", "B", "C");
                assertThat(queue.depth()).isZero();
            });
        }
    }

    @Test
    void concurrentAppendDuringSlowPublishStaysBehind() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ControllableLink link = new ControllableLink() {
            @Override
            public void publish(TelemetryRecord record) throws LinkUnavailableException {
                if ("A".equals(record.tagCode())) {
                    started.countDown();
                    try {
                        if (!release.await(5, TimeUnit.SECONDS)) {
                            throw new LinkUnavailableException("release timeout");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new LinkUnavailableException("interrupted", e);
                    }
                }
                super.publish(record);
            }
        };

        try (StoreAndForwardQueue queue = newQueue(link, 100)) {
            Thread publisher = new Thread(() -> queue.offer(record("A", 1)), "slow-publish");
            publisher.start();
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

            queue.offer(record("B", 2));
            queue.offer(record("C", 3));
            // Fast path keeps A in SQLite until publish succeeds, so depth is A+B+C.
            assertThat(queue.depth()).isEqualTo(3);

            release.countDown();
            publisher.join(TimeUnit.SECONDS.toMillis(5));

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                assertThat(link.published.stream().map(TelemetryRecord::tagCode).toList())
                        .containsExactly("A", "B", "C");
                assertThat(queue.depth()).isZero();
            });
        }
    }

    @Test
    void pacedDrainDoesNotStallOffer() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ControllableLink link = new ControllableLink() {
            @Override
            public void publish(TelemetryRecord record) throws LinkUnavailableException {
                if ("A".equals(record.tagCode()) && started.getCount() > 0) {
                    started.countDown();
                    try {
                        if (!release.await(5, TimeUnit.SECONDS)) {
                            throw new LinkUnavailableException("release timeout");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new LinkUnavailableException("interrupted", e);
                    }
                }
                super.publish(record);
            }
        };
        link.setAvailable(false);

        try (StoreAndForwardQueue queue =
                new StoreAndForwardQueue(
                        link, tempDir.resolve("paced.db"), 100, 1, 5L, new ObjectMapper())) {
            queue.offer(record("A", 1));
            queue.offer(record("B", 2));

            link.setAvailable(true);
            queue.onLinkRecovered();
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

            long offerStart = System.nanoTime();
            queue.offer(record("C", 3));
            long offerMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - offerStart);
            assertThat(offerMs).isLessThan(200);
            assertThat(queue.depth()).isGreaterThanOrEqualTo(2);

            release.countDown();

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                assertThat(link.published.stream().map(TelemetryRecord::tagCode).toList())
                        .containsExactly("A", "B", "C");
                assertThat(queue.depth()).isZero();
            });
        }
    }

    @Test
    void closeWakesLongDrainPause() {
        ControllableLink link = new ControllableLink();
        link.setAvailable(false);
        long closeStarted;
        long closeMs;
        try (StoreAndForwardQueue queue =
                new StoreAndForwardQueue(
                        link, tempDir.resolve("pause-close.db"), 100, 1, 60_000L, new ObjectMapper())) {
            queue.offer(record("A", 1));
            queue.offer(record("B", 2));
            link.setAvailable(true);
            queue.onLinkRecovered();
            // Batch size 1: after A publishes, worker pauses 60s before B.
            await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
                assertThat(link.published.stream().map(TelemetryRecord::tagCode).toList())
                        .containsExactly("A");
                assertThat(queue.depth()).isEqualTo(1);
            });

            closeStarted = System.nanoTime();
        }
        closeMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - closeStarted);
        assertThat(closeMs).isLessThan(5_000);
    }

    @Test
    void evictionDropsOldestWaitingAndLogs(CapturedOutput output) {
        ControllableLink link = new ControllableLink();
        link.setAvailable(false);
        try (StoreAndForwardQueue queue = newQueue(link, 2)) {
            queue.offer(record("A", 1));
            queue.offer(record("B", 2));
            queue.offer(record("C", 3));

            assertThat(queue.depth()).isEqualTo(2);
            assertThat(output.getOut() + output.getErr())
                    .contains("EDGE buffer eviction")
                    .contains("oldest telemetry dropped");

            link.setAvailable(true);
            queue.onLinkRecovered();
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(
                            link.published.stream().map(TelemetryRecord::tagCode).toList())
                    .containsExactly("B", "C"));
        }
    }

    @Test
    void evictionSkipsInflightHead() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ControllableLink link = new ControllableLink() {
            @Override
            public void publish(TelemetryRecord record) throws LinkUnavailableException {
                if ("A".equals(record.tagCode()) && started.getCount() > 0) {
                    started.countDown();
                    try {
                        if (!release.await(5, TimeUnit.SECONDS)) {
                            throw new LinkUnavailableException("release timeout");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new LinkUnavailableException("interrupted", e);
                    }
                }
                super.publish(record);
            }
        };
        link.setAvailable(false);

        try (StoreAndForwardQueue queue = newQueue(link, 2)) {
            queue.offer(record("A", 1));
            queue.offer(record("B", 2));

            link.setAvailable(true);
            queue.onLinkRecovered();
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

            // Depth still 2 while A is in flight; offer C must evict B (not A)
            queue.offer(record("C", 3));
            release.countDown();

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                assertThat(link.published.stream().map(TelemetryRecord::tagCode).toList())
                        .containsExactly("A", "C");
                assertThat(queue.depth()).isZero();
            });
        }
    }

    @Test
    void reopenReplaysRemainder() {
        Path db = tempDir.resolve("reopen.db");
        ControllableLink link = new ControllableLink();
        link.setAvailable(false);
        try (StoreAndForwardQueue queue =
                new StoreAndForwardQueue(link, db, 100, new ObjectMapper())) {
            queue.offer(record("A", 1));
            queue.offer(record("B", 2));
        }

        ControllableLink link2 = new ControllableLink();
        try (StoreAndForwardQueue queue2 =
                new StoreAndForwardQueue(link2, db, 100, new ObjectMapper())) {
            assertThat(queue2.depth()).isEqualTo(2);
            queue2.onLinkRecovered();
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(
                            link2.published.stream().map(TelemetryRecord::tagCode).toList())
                    .containsExactly("A", "B"));
        }
    }

    @Test
    void reopenLogsResidualDepth(CapturedOutput output) {
        Path db = tempDir.resolve("residual.db");
        ControllableLink link = new ControllableLink();
        link.setAvailable(false);
        try (StoreAndForwardQueue queue =
                new StoreAndForwardQueue(link, db, 100, new ObjectMapper())) {
            queue.offer(record("A", 1));
            queue.offer(record("B", 2));
        }

        ControllableLink link2 = new ControllableLink();
        link2.setAvailable(false);
        try (StoreAndForwardQueue ignored =
                new StoreAndForwardQueue(link2, db, 100, new ObjectMapper())) {
            assertThat(output.getOut() + output.getErr()).contains("residualDepth=2");
        }
    }

    @Test
    void roundTripsTypedValuesAndNullBadRecord() {
        ControllableLink link = new ControllableLink();
        link.setAvailable(false);
        try (StoreAndForwardQueue queue = newQueue(link, 100)) {
            queue.offer(typed("INT", 1, TelemetryRecord.TYPE_INT32));
            queue.offer(typed("LONG", 2L, TelemetryRecord.TYPE_INT64));
            queue.offer(typed("FLOAT", 1.5f, TelemetryRecord.TYPE_FLOAT));
            queue.offer(typed("DOUBLE", 2.5d, TelemetryRecord.TYPE_DOUBLE));
            queue.offer(typed("BOOL", true, TelemetryRecord.TYPE_BOOLEAN));
            queue.offer(typed("STR", "ok", TelemetryRecord.TYPE_STRING));
            queue.offer(
                    new TelemetryRecord(
                            "INDORE",
                            "LINE-01",
                            "BAD_TAG",
                            "nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=9",
                            null,
                            TelemetryRecord.TYPE_NULL,
                            "BAD",
                            "Bad_Timeout",
                            null,
                            null,
                            null));

            link.setAvailable(true);
            queue.onLinkRecovered();

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(link.published).hasSize(7));
            assertThat(link.published.get(0).value()).isInstanceOf(Integer.class).isEqualTo(1);
            assertThat(link.published.get(1).value()).isInstanceOf(Long.class).isEqualTo(2L);
            assertThat(link.published.get(2).value()).isInstanceOf(Float.class);
            assertThat(link.published.get(3).value()).isInstanceOf(Double.class).isEqualTo(2.5d);
            assertThat(link.published.get(4).value()).isEqualTo(true);
            assertThat(link.published.get(5).value()).isEqualTo("ok");
            TelemetryRecord bad = link.published.get(6);
            assertThat(bad.value()).isNull();
            assertThat(bad.valueType()).isEqualTo(TelemetryRecord.TYPE_NULL);
            assertThat(bad.sourceTimestamp()).isNull();
            assertThat(bad.serverTimestamp()).isNull();
            assertThat(bad.statusName()).isEqualTo("Bad_Timeout");
        }
    }

    private StoreAndForwardQueue newQueue(TelemetryLink link, int max) {
        return new StoreAndForwardQueue(
                link, tempDir.resolve("edge-" + System.nanoTime() + ".db"), max, new ObjectMapper());
    }

    private static TelemetryRecord record(String tagCode, Object value) {
        return typed(tagCode, value, TelemetryRecord.typeOf(value));
    }

    private static TelemetryRecord typed(String tagCode, Object value, String valueType) {
        return new TelemetryRecord(
                "INDORE",
                "LINE-01",
                tagCode,
                "nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=1001",
                value,
                valueType,
                "GOOD",
                null,
                Instant.parse("2026-11-20T12:00:00Z"),
                Instant.parse("2026-11-20T12:00:01Z"),
                null);
    }

    private static class ControllableLink implements TelemetryLink {
        final List<TelemetryRecord> published = new CopyOnWriteArrayList<>();
        final List<String> failOnTagCodes = new ArrayList<>();
        private final AtomicBoolean available = new AtomicBoolean(true);
        private final AtomicInteger publishCount = new AtomicInteger();

        void setAvailable(boolean value) {
            available.set(value);
        }

        @Override
        public void publish(TelemetryRecord record) throws LinkUnavailableException {
            publishCount.incrementAndGet();
            if (!available.get() || failOnTagCodes.contains(record.tagCode())) {
                throw new LinkUnavailableException("link down for " + record.tagCode());
            }
            published.add(record);
        }

        @Override
        public boolean isAvailable() {
            return available.get();
        }
    }
}
