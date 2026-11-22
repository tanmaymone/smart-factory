package com.factory.edge.opcua;

import com.factory.edge.buffer.StoreAndForwardQueue;
import com.factory.edge.buffer.TelemetryRecord;
import com.factory.edge.filter.DeadbandFilter;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
public class OpcUaProbe implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(OpcUaProbe.class);

    enum Phase {
        STOPPED,
        STARTING,
        UP,
        BACKOFF
    }

    private final OpcUaProperties properties;
    private final OpcUaSession session;
    private final StoreAndForwardQueue queue;
    private final OpcUaDiagnosticsSink diagnostics;
    private final ScheduledExecutorService scheduler;
    private final boolean ownsScheduler;

    private final AtomicReference<Phase> phase = new AtomicReference<>(Phase.STOPPED);
    private final AtomicLong backoffMs = new AtomicLong();
    private final AtomicReference<ScheduledFuture<?>> pendingReconnect = new AtomicReference<>();

    private DeadbandFilter filter;

    @Autowired
    public OpcUaProbe(
            OpcUaProperties properties,
            OpcUaSession session,
            StoreAndForwardQueue queue,
            OpcUaDiagnosticsSink diagnostics) {
        this(
                properties,
                session,
                queue,
                diagnostics,
                Executors.newSingleThreadScheduledExecutor(
                        r -> {
                            Thread t = new Thread(r, "opcua-reconnect");
                            t.setDaemon(true);
                            return t;
                        }),
                true);
    }

    /** Test-visible constructor with an injectable scheduler. */
    OpcUaProbe(
            OpcUaProperties properties,
            OpcUaSession session,
            StoreAndForwardQueue queue,
            ScheduledExecutorService scheduler) {
        this(properties, session, queue, new OpcUaDiagnosticsSink(), scheduler, false);
    }

    private OpcUaProbe(
            OpcUaProperties properties,
            OpcUaSession session,
            StoreAndForwardQueue queue,
            OpcUaDiagnosticsSink diagnostics,
            ScheduledExecutorService scheduler,
            boolean ownsScheduler) {
        this.properties = properties;
        this.session = session;
        this.queue = queue;
        this.diagnostics = diagnostics;
        this.scheduler = scheduler;
        this.ownsScheduler = ownsScheduler;
        this.backoffMs.set(properties.getReconnectInitialMs());
    }

    @Override
    public void start() {
        List<OpcUaProperties.TagConfig> tags = properties.getTags();
        if (tags == null || tags.isEmpty()) {
            throw new OpcUaProbeException("OPC UA tag list is empty");
        }
        if (!phase.compareAndSet(Phase.STOPPED, Phase.STARTING)) {
            return;
        }

        filter = new DeadbandFilter(tags);
        backoffMs.set(properties.getReconnectInitialMs());
        diagnostics.markStarting();
        try {
            session.start(tags, this::onReading, this::onSessionLost);
            phase.set(Phase.UP);
            diagnostics.markUp();
            log.info(
                    "OPC UA probe running endpoint={} tags={}",
                    properties.getEndpointUrl(),
                    tags.size());
        } catch (OpcUaUnavailableException e) {
            String operatorMessage =
                    "OPC UA server is not live at "
                            + properties.getEndpointUrl()
                            + ". Start it; the edge will connect automatically.";
            phase.set(Phase.BACKOFF);
            diagnostics.markReconnectFailed(operatorMessage);
            log.warn(
                    "{}; scheduling reconnect in {}ms: {}",
                    operatorMessage,
                    backoffMs.get(),
                    e.getMessage());
            scheduleReconnect(backoffMs.get());
        } catch (RuntimeException e) {
            phase.set(Phase.STOPPED);
            diagnostics.markStopped();
            throw e;
        }
    }

    @Override
    public void stop() {
        phase.set(Phase.STOPPED);
        diagnostics.markStopped();
        cancelPendingReconnect();
        try {
            session.close();
        } catch (Exception e) {
            log.debug("Session close on stop: {}", e.toString());
        }
        if (ownsScheduler) {
            scheduler.shutdownNow();
        }
    }

    @Override
    public boolean isRunning() {
        Phase p = phase.get();
        return p == Phase.UP || p == Phase.STARTING || p == Phase.BACKOFF;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return 0;
    }

    Phase currentPhase() {
        return phase.get();
    }

    long currentBackoffMs() {
        return backoffMs.get();
    }

    OpcUaLinkHealth linkHealth() {
        return diagnostics.linkHealth();
    }

    TagObservationStore observations() {
        return diagnostics.observations();
    }

    private void onReading(TagReading reading) {
        DeadbandFilter active = filter;
        boolean emit = active != null && active.shouldEmit(reading);
        diagnostics.onReading(reading, emit);
        if (!emit) {
            return;
        }
        logReading(reading);
        queue.offer(TelemetryRecord.from(reading));
    }

    private void logReading(TagReading reading) {
        String line = TagReadingFormatter.format(reading);
        if ("BAD".equals(reading.quality())) {
            log.error(line);
        } else if ("UNCERTAIN".equals(reading.quality())) {
            log.warn(line);
        } else {
            log.info(line);
        }
    }

    private void onSessionLost(long generation) {
        if (!phase.compareAndSet(Phase.UP, Phase.BACKOFF)) {
            return;
        }
        diagnostics.markLost(
                "session lost generation=" + generation + " endpoint=" + properties.getEndpointUrl());
        log.warn(
                "OPC UA session lost for {} (generation={}); scheduling reconnect in {}ms",
                properties.getEndpointUrl(),
                generation,
                backoffMs.get());
        try {
            session.close();
        } catch (Exception e) {
            log.debug("Disconnect after session loss: {}", e.toString());
        }
        scheduleReconnect(backoffMs.get());
    }

    private void scheduleReconnect(long delayMs) {
        cancelPendingReconnect();
        pendingReconnect.set(
                scheduler.schedule(this::reconnectAttempt, delayMs, TimeUnit.MILLISECONDS));
    }

    private void reconnectAttempt() {
        if (phase.get() == Phase.STOPPED) {
            return;
        }
        if (!phase.compareAndSet(Phase.BACKOFF, Phase.STARTING)) {
            return;
        }
        diagnostics.markStarting();
        List<OpcUaProperties.TagConfig> tags = properties.getTags();
        try {
            session.close();
            session.start(tags, this::onReading, this::onSessionLost);
            backoffMs.set(properties.getReconnectInitialMs());
            phase.set(Phase.UP);
            diagnostics.markUp();
            log.info(
                    "OPC UA probe reconnected endpoint={} tags={}",
                    properties.getEndpointUrl(),
                    tags.size());
        } catch (RuntimeException e) {
            try {
                session.close();
            } catch (Exception closeEx) {
                log.debug("Close after failed reconnect: {}", closeEx.toString());
            }
            long next =
                    Math.min(
                            Math.max(backoffMs.get() * 2, properties.getReconnectInitialMs()),
                            properties.getReconnectMaxMs());
            backoffMs.set(next);
            phase.set(Phase.BACKOFF);
            diagnostics.markReconnectFailed(e.getMessage());
            log.warn(
                    "OPC UA reconnect failed for {}; next attempt in {}ms: {}",
                    properties.getEndpointUrl(),
                    next,
                    e.getMessage());
            scheduleReconnect(next);
        }
    }

    private void cancelPendingReconnect() {
        ScheduledFuture<?> future = pendingReconnect.getAndSet(null);
        if (future != null) {
            future.cancel(false);
        }
    }
}
