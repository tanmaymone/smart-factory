package com.factory.central.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.factory.central.ingestion.TelemetryBatchWriter.EnqueueOutcome;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("SF-M1.4")
class TelemetryBatchWriterTest {

    @Test
    void scheduledFlushSkipsWhenHttpHoldsFlushLock() throws Exception {
        TelemetryHistoryRepository repository = mock(TelemetryHistoryRepository.class);
        CountDownLatch inFlush = new CountDownLatch(1);
        CountDownLatch releaseFlush = new CountDownLatch(1);
        AtomicInteger writes = new AtomicInteger();

        doAnswer(invocation -> {
                    writes.incrementAndGet();
                    inFlush.countDown();
                    releaseFlush.await(5, TimeUnit.SECONDS);
                    return null;
                })
                .when(repository)
                .batchInsert(anyList());

        TelemetryProperties properties = new TelemetryProperties();
        properties.setQueueCapacity(10);
        properties.setBatchSize(10);
        TelemetryBatchWriter writer =
                new TelemetryBatchWriter(repository, properties, new SimpleMeterRegistry());

        Thread http = new Thread(() -> writer.enqueueHttpAndFlush(List.of(row("A"))));
        http.start();
        assertThat(inFlush.await(5, TimeUnit.SECONDS)).isTrue();

        writer.scheduledFlush();
        releaseFlush.countDown();
        http.join(TimeUnit.SECONDS.toMillis(5));

        verify(repository, times(1)).batchInsert(anyList());
        assertThat(writes.get()).isEqualTo(1);
    }

    @Test
    void failedBatchRetriedBeforeNewerRowsThenDroppedOnSecondFailure() {
        TelemetryHistoryRepository repository = mock(TelemetryHistoryRepository.class);
        AtomicInteger attempts = new AtomicInteger();
        List<List<TelemetryHistoryRow>> seen = new ArrayList<>();

        doAnswer(invocation -> {
                    List<TelemetryHistoryRow> batch = invocation.getArgument(0);
                    seen.add(List.copyOf(batch));
                    if (attempts.getAndIncrement() < 2) {
                        throw new RuntimeException("db down");
                    }
                    return null;
                })
                .when(repository)
                .batchInsert(anyList());

        TelemetryProperties properties = new TelemetryProperties();
        properties.setQueueCapacity(10);
        properties.setBatchSize(1);
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        TelemetryBatchWriter writer = new TelemetryBatchWriter(repository, properties, meters);

        assertThat(writer.enqueueMqtt(List.of(row("first")), "INDORE", "LINE-01", "m1"))
                .isEqualTo(EnqueueOutcome.OK);
        writer.scheduledFlush(); // fail 1, keep inFlight
        assertThat(writer.enqueueMqtt(List.of(row("second")), "INDORE", "LINE-01", "m2"))
                .isEqualTo(EnqueueOutcome.OK);
        writer.scheduledFlush(); // fail 2 -> drop first
        writer.scheduledFlush(); // write second

        assertThat(seen.get(0).getFirst().tagCode()).isEqualTo("first");
        assertThat(seen.get(1).getFirst().tagCode()).isEqualTo("first");
        assertThat(seen.get(2).getFirst().tagCode()).isEqualTo("second");
        assertThat(meters.counter("telemetry.ingest.dropped", "source", "writer", "reason", "write_failed").count())
                .isEqualTo(1.0);
    }

    @Test
    void overflowDoesNotDropOldestAndCountsMqttDrops() {
        TelemetryHistoryRepository hanging = mock(TelemetryHistoryRepository.class);
        doThrow(new RuntimeException("still blocked")).when(hanging).batchInsert(anyList());

        TelemetryProperties properties = new TelemetryProperties();
        properties.setQueueCapacity(2);
        properties.setBatchSize(2);
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        TelemetryBatchWriter writer = new TelemetryBatchWriter(hanging, properties, meters);

        // Fill queue, move to inFlight on failed flush, refill queue, then reject without dropping queued rows.
        assertThat(writer.enqueueMqtt(List.of(row("a"), row("b")), "INDORE", "LINE-01", "m1"))
                .isEqualTo(EnqueueOutcome.OK);
        writer.scheduledFlush(); // inFlight = a,b (first failure)
        assertThat(writer.enqueueMqtt(List.of(row("c"), row("d")), "INDORE", "LINE-01", "m2"))
                .isEqualTo(EnqueueOutcome.OK);
        assertThat(writer.queueSize()).isEqualTo(2);
        assertThat(writer.enqueueMqtt(List.of(row("e")), "INDORE", "LINE-01", "m3"))
                .isEqualTo(EnqueueOutcome.BUFFER_FULL);
        assertThat(writer.queueSize()).isEqualTo(2);
        assertThat(meters.counter("telemetry.ingest.dropped", "source", "mqtt", "reason", "buffer_full").count())
                .isEqualTo(1.0);
        assertThat(meters.counter("telemetry.ingest.dropped", "source", "writer", "reason", "write_failed").count())
                .isEqualTo(2.0);
    }

    private static TelemetryHistoryRow row(String tagCode) {
        return new TelemetryHistoryRow(
                Instant.parse("2026-11-19T12:00:00Z"),
                "INDORE",
                "LINE-01",
                tagCode,
                1.0,
                null,
                null,
                "GOOD");
    }
}
