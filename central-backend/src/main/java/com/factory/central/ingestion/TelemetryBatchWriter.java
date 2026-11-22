package com.factory.central.ingestion;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class TelemetryBatchWriter {

    private static final Logger log = LoggerFactory.getLogger(TelemetryBatchWriter.class);

    private final TelemetryHistoryRepository repository;
    private final TelemetryProperties properties;
    private final MeterRegistry meterRegistry;
    private final ArrayBlockingQueue<QueuedRow> queue;
    private final Object drainLock = new Object();
    private final ReentrantLock flushLock = new ReentrantLock();

    private List<QueuedRow> inFlight;
    private boolean inFlightRetried;
    private long nextSeq = 1L;
    private long lastDurableSeq = 0L;

    public TelemetryBatchWriter(
            TelemetryHistoryRepository repository, TelemetryProperties properties, MeterRegistry meterRegistry) {
        this.repository = repository;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.queue = new ArrayBlockingQueue<>(properties.getQueueCapacity());
    }

    public EnqueueOutcome enqueueHttpAndFlush(List<TelemetryHistoryRow> rows) {
        if (rows.isEmpty()) {
            return EnqueueOutcome.OK;
        }
        flushLock.lock();
        try {
            if (!enqueueAllOrNothing(rows)) {
                flushUnlocked();
                if (!enqueueAllOrNothing(rows)) {
                    return EnqueueOutcome.BUFFER_FULL;
                }
            }
            long watermark = lastEnqueuedSeq();
            while (lastDurableSeq < watermark) {
                boolean progressed = flushUnlocked();
                if (!progressed && lastDurableSeq < watermark) {
                    return EnqueueOutcome.BUFFER_FULL;
                }
            }
            return EnqueueOutcome.OK;
        } finally {
            flushLock.unlock();
        }
    }

    public EnqueueOutcome enqueueMqtt(List<TelemetryHistoryRow> rows, String siteId, String lineId, String messageId) {
        if (rows.isEmpty()) {
            return EnqueueOutcome.OK;
        }
        if (enqueueAllOrNothing(rows)) {
            return EnqueueOutcome.OK;
        }
        flushLock.lock();
        try {
            flushUnlocked();
            if (enqueueAllOrNothing(rows)) {
                return EnqueueOutcome.OK;
            }
            log.warn(
                    "Dropping MQTT telemetry rows source=mqtt site={} line={} messageId={} count={} reason=buffer_full",
                    siteId,
                    lineId,
                    messageId,
                    rows.size());
            meterRegistry
                    .counter("telemetry.ingest.dropped", "source", "mqtt", "reason", "buffer_full")
                    .increment(rows.size());
            return EnqueueOutcome.BUFFER_FULL;
        } finally {
            flushLock.unlock();
        }
    }

    public void scheduledFlush() {
        if (!flushLock.tryLock()) {
            return;
        }
        try {
            flushUnlocked();
        } finally {
            flushLock.unlock();
        }
    }

    /** Test/support hook: current queued size excluding in-flight. */
    int queueSize() {
        synchronized (drainLock) {
            return queue.size();
        }
    }

    private boolean enqueueAllOrNothing(List<TelemetryHistoryRow> rows) {
        synchronized (drainLock) {
            if (queue.remainingCapacity() < rows.size()) {
                return false;
            }
            for (TelemetryHistoryRow row : rows) {
                boolean offered = queue.offer(new QueuedRow(nextSeq++, row));
                if (!offered) {
                    throw new IllegalStateException("Queue rejected row after capacity check");
                }
            }
            return true;
        }
    }

    private long lastEnqueuedSeq() {
        synchronized (drainLock) {
            return nextSeq - 1L;
        }
    }

    /**
     * @return true when a write was attempted or in-flight was cleared; false when there was nothing to flush
     */
    private boolean flushUnlocked() {
        List<QueuedRow> batch;
        if (inFlight != null) {
            batch = inFlight;
        } else {
            synchronized (drainLock) {
                if (queue.isEmpty()) {
                    return false;
                }
                batch = new ArrayList<>(Math.min(properties.getBatchSize(), queue.size()));
                queue.drainTo(batch, properties.getBatchSize());
            }
            if (batch.isEmpty()) {
                return false;
            }
            inFlight = batch;
            inFlightRetried = false;
        }

        try {
            repository.batchInsert(batch.stream().map(QueuedRow::row).toList());
            long maxSeq = batch.get(batch.size() - 1).seq();
            lastDurableSeq = Math.max(lastDurableSeq, maxSeq);
            inFlight = null;
            inFlightRetried = false;
            return true;
        } catch (RuntimeException ex) {
            if (inFlightRetried) {
                log.warn(
                        "Dropping telemetry batch after write retry failed count={} reason=write_failed",
                        batch.size(),
                        ex);
                meterRegistry
                        .counter("telemetry.ingest.dropped", "source", "writer", "reason", "write_failed")
                        .increment(batch.size());
                inFlight = null;
                inFlightRetried = false;
                return true;
            }
            inFlightRetried = true;
            log.warn("Telemetry batch write failed; will retry once count={}", batch.size(), ex);
            return true;
        }
    }

    public enum EnqueueOutcome {
        OK,
        BUFFER_FULL
    }

    private record QueuedRow(long seq, TelemetryHistoryRow row) {}
}
