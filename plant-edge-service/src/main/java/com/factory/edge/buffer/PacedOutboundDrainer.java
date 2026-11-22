package com.factory.edge.buffer;

import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Single-worker paced drain: kicks wake the loop; each tick publishes a capped batch, then pauses
 * while backlog remains. {@link #close()} interrupts any pause wait immediately.
 */
final class PacedOutboundDrainer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(PacedOutboundDrainer.class);

    private final Object pauseLock = new Object();
    private final AtomicBoolean pending = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final DrainTick tick;
    private final int batchSize;
    private final long pauseMs;
    private final Thread worker;

    PacedOutboundDrainer(String threadName, int batchSize, long pauseMs, DrainTick tick) {
        this.tick = tick;
        this.batchSize = Math.max(1, batchSize);
        this.pauseMs = Math.max(0, pauseMs);
        this.worker = new Thread(this::runLoop, threadName);
        this.worker.setDaemon(true);
        this.worker.start();
    }

    void kick() {
        pending.set(true);
        synchronized (pauseLock) {
            pauseLock.notifyAll();
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        kick();
        worker.interrupt();
        try {
            worker.join(5_000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.debug("EDGE drain join interrupted");
        }
    }

    private void runLoop() {
        while (!closed.get()) {
            synchronized (pauseLock) {
                while (!closed.get() && !pending.get()) {
                    try {
                        pauseLock.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
            if (closed.get()) {
                return;
            }
            pending.set(false);

            while (!closed.get()) {
                DrainResult result = tick.drainBatch(batchSize);
                if (result != DrainResult.MORE) {
                    break;
                }
                if (!awaitPause()) {
                    return;
                }
            }
        }
    }

    /** @return false when closed or interrupted (caller must exit) */
    private boolean awaitPause() {
        if (closed.get()) {
            return false;
        }
        if (pauseMs == 0) {
            Thread.yield();
            return !closed.get();
        }
        synchronized (pauseLock) {
            long deadline = System.nanoTime() + pauseMs * 1_000_000L;
            while (!closed.get()) {
                long remainingNs = deadline - System.nanoTime();
                if (remainingNs <= 0) {
                    return true;
                }
                try {
                    pauseLock.wait(remainingNs / 1_000_000L, (int) (remainingNs % 1_000_000L));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return false;
        }
    }

    @FunctionalInterface
    interface DrainTick {
        DrainResult drainBatch(int maxPublishes);
    }

    enum DrainResult {
        /** Backlog remains and link is up — pause then continue. */
        MORE,
        /** Empty, link down, or another publisher holds inflight — wait for next kick. */
        IDLE
    }
}
