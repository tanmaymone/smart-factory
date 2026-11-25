package com.factory.edge.opcua;

import com.factory.edge.buffer.StoreAndForwardQueue;
import com.factory.edge.command.CommandDispatcher;
import com.factory.edge.config.ActiveTagCatalog;
import com.factory.edge.config.EdgeApplyState;
import com.factory.edge.config.EdgeConfigProperties;
import com.factory.edge.config.EdgeConfigReconcileHook;
import com.factory.edge.config.EdgeConfigSync;
import com.factory.edge.filter.DeadbandFilter;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * OPC UA lifecycle owner. In static mode it starts the YAML tag list and reconnects on loss; with
 * config sync on it starts IDLE and lets {@link OpcUaReconciler} drive every transition.
 */
@Component
public class OpcUaProbe implements SmartLifecycle, EdgeConfigReconcileHook {

    private static final Logger log = LoggerFactory.getLogger(OpcUaProbe.class);

    enum Phase {
        STOPPED,
        STARTING,
        UP,
        BACKOFF,
        /** Connected to nothing on purpose: no desired tags, or every desired tag was rejected. */
        IDLE
    }

    /** Config-sync collaborators; all null in static mode. */
    record SyncDeps(
            EdgeConfigProperties edgeConfig,
            EdgeConfigSync sync,
            ActiveTagCatalog catalog,
            EdgeApplyState applyState,
            CommandDispatcher dispatcher) {

        boolean enabled() {
            return edgeConfig != null && edgeConfig.isConfigSyncEnabled();
        }
    }

    private final OpcUaProperties properties;
    private final OpcUaSession session;
    private final OpcUaDiagnosticsSink diagnostics;
    private final ReadingPipeline pipeline;
    private final ScheduledExecutorService scheduler;
    private final boolean ownsScheduler;
    private final SyncDeps deps;
    private final OpcUaReconciler reconciler;

    private final AtomicReference<Phase> phase = new AtomicReference<>(Phase.STOPPED);
    private final AtomicLong backoffMs = new AtomicLong();
    private final AtomicReference<ScheduledFuture<?>> pendingReconnect = new AtomicReference<>();
    private final AtomicBoolean intentionalClose = new AtomicBoolean();

    @Autowired
    public OpcUaProbe(
            OpcUaProperties properties,
            OpcUaSession session,
            StoreAndForwardQueue queue,
            OpcUaDiagnosticsSink diagnostics,
            EdgeConfigProperties edgeConfig,
            EdgeConfigSync sync,
            ActiveTagCatalog catalog,
            EdgeApplyState applyState,
            CommandDispatcher dispatcher) {
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
                true,
                new SyncDeps(edgeConfig, sync, catalog, applyState, dispatcher));
    }

    /** Test-visible static-mode constructor with an injectable scheduler. */
    OpcUaProbe(
            OpcUaProperties properties,
            OpcUaSession session,
            StoreAndForwardQueue queue,
            ScheduledExecutorService scheduler) {
        this(
                properties,
                session,
                queue,
                new OpcUaDiagnosticsSink(),
                scheduler,
                false,
                new SyncDeps(new EdgeConfigProperties(), null, null, null, null));
    }

    OpcUaProbe(
            OpcUaProperties properties,
            OpcUaSession session,
            StoreAndForwardQueue queue,
            OpcUaDiagnosticsSink diagnostics,
            ScheduledExecutorService scheduler,
            boolean ownsScheduler,
            SyncDeps deps) {
        this.properties = properties;
        this.session = session;
        this.diagnostics = diagnostics;
        this.pipeline = new ReadingPipeline(diagnostics, queue);
        this.scheduler = scheduler;
        this.ownsScheduler = ownsScheduler;
        this.deps = deps;
        this.backoffMs.set(properties.getReconnectInitialMs());
        this.reconciler =
                deps.enabled()
                        ? new OpcUaReconciler(
                                this,
                                session,
                                deps.sync(),
                                deps.catalog(),
                                deps.applyState(),
                                deps.dispatcher(),
                                diagnostics.observations())
                        : null;
    }

    @Override
    public void start() {
        if (reconciler != null) {
            startIdle();
            return;
        }
        startFromYaml();
    }

    private void startIdle() {
        if (!phase.compareAndSet(Phase.STOPPED, Phase.IDLE)) {
            return;
        }
        diagnostics.markIdle();
        log.info(
                "OPC UA probe IDLE awaiting central config siteId={} endpoint={}",
                deps.edgeConfig().getNormalizedSiteId(),
                properties.getEndpointUrl());
        requestReconcile();
    }

    private void startFromYaml() {
        List<OpcUaProperties.TagConfig> tags = properties.getTags();
        if (tags == null || tags.isEmpty()) {
            throw new OpcUaProbeException("OPC UA tag list is empty");
        }
        if (!phase.compareAndSet(Phase.STOPPED, Phase.STARTING)) {
            return;
        }
        pipeline.replaceFilter(new DeadbandFilter(tags));
        diagnostics.markStarting();
        try {
            session.start(tags, pipeline::accept, this::onSessionLost);
            markUp(tags.size());
        } catch (OpcUaUnavailableException e) {
            failToBackoff(e, false);
        } catch (RuntimeException e) {
            phase.set(Phase.STOPPED);
            diagnostics.markStopped();
            throw e;
        }
    }

    @Override
    public void requestReconcile() {
        if (reconciler == null || phase.get() == Phase.STOPPED) {
            return;
        }
        try {
            scheduler.execute(
                    () -> {
                        try {
                            reconciler.reconcile();
                        } catch (Exception e) {
                            log.warn("EDGE CONFIG reconcile failed: {}", e.toString());
                        }
                    });
        } catch (RejectedExecutionException e) {
            log.debug("Reconcile rejected; probe is shutting down");
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

    /** IDLE keeps the lifecycle up so MQTT telemetry, commands, and diagnostics keep serving. */
    @Override
    public boolean isRunning() {
        return phase.get() != Phase.STOPPED;
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

    void setPhase(Phase next) {
        phase.set(next);
    }

    long currentBackoffMs() {
        return backoffMs.get();
    }

    DeadbandFilter filter() {
        return pipeline.filter();
    }

    void replaceFilter(DeadbandFilter next) {
        pipeline.replaceFilter(next);
    }

    Consumer<TagReading> readingSink() {
        return pipeline::accept;
    }

    LongConsumer sessionLostSink() {
        return this::onSessionLost;
    }

    OpcUaLinkHealth linkHealth() {
        return diagnostics.linkHealth();
    }

    TagObservationStore observations() {
        return diagnostics.observations();
    }

    void markUp(int tagCount) {
        backoffMs.set(properties.getReconnectInitialMs());
        intentionalClose.set(false);
        phase.set(Phase.UP);
        diagnostics.markUp();
        log.info("OPC UA probe running endpoint={} tags={}", properties.getEndpointUrl(), tagCount);
    }

    void markStarting() {
        phase.set(Phase.STARTING);
        diagnostics.markStarting();
    }

    /** Empty desired set: close on purpose, stay IDLE, and never schedule a reconnect. */
    void toIdle(boolean closeSession) {
        cancelPendingReconnect();
        if (closeSession) {
            intentionalClose.set(true);
            try {
                session.close();
            } catch (Exception e) {
                log.debug("Session close for IDLE: {}", e.toString());
            }
        }
        phase.set(Phase.IDLE);
        diagnostics.markIdle();
        log.info("OPC UA probe IDLE (no active tags) endpoint={}", properties.getEndpointUrl());
    }

    void failToBackoff(RuntimeException cause, boolean advanceBackoff) {
        try {
            session.close();
        } catch (Exception closeEx) {
            log.debug("Close after failed start: {}", closeEx.toString());
        }
        if (advanceBackoff) {
            backoffMs.set(
                    Math.min(
                            Math.max(backoffMs.get() * 2, properties.getReconnectInitialMs()),
                            properties.getReconnectMaxMs()));
        }
        String operatorMessage =
                cause instanceof OpcUaUnavailableException
                        ? "OPC UA server is not live at "
                                + properties.getEndpointUrl()
                                + ". Start it; the edge will connect automatically."
                        : cause.getMessage();
        phase.set(Phase.BACKOFF);
        diagnostics.markReconnectFailed(operatorMessage);
        log.warn(
                "{}; scheduling reconnect in {}ms: {}",
                operatorMessage,
                backoffMs.get(),
                cause.getMessage());
        scheduleReconnect(backoffMs.get());
    }

    private void onSessionLost(long generation) {
        if (intentionalClose.compareAndSet(true, false)) {
            return;
        }
        if (reconciler != null && reconciler.desiredIsEmpty()) {
            phase.set(Phase.IDLE);
            diagnostics.markIdle();
            return;
        }
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
        try {
            pendingReconnect.set(
                    scheduler.schedule(this::reconnectAttempt, delayMs, TimeUnit.MILLISECONDS));
        } catch (RejectedExecutionException e) {
            log.debug("Reconnect not scheduled; probe is shutting down");
        }
    }

    private void reconnectAttempt() {
        if (phase.get() == Phase.STOPPED) {
            return;
        }
        if (!phase.compareAndSet(Phase.BACKOFF, Phase.STARTING)) {
            return;
        }
        diagnostics.markStarting();
        if (reconciler != null) {
            reconciler.reconnect();
            return;
        }
        List<OpcUaProperties.TagConfig> tags = properties.getTags();
        try {
            session.close();
            session.start(tags, pipeline::accept, this::onSessionLost);
            markUp(tags.size());
        } catch (RuntimeException e) {
            failToBackoff(e, true);
        }
    }

    private void cancelPendingReconnect() {
        ScheduledFuture<?> future = pendingReconnect.getAndSet(null);
        if (future != null) {
            future.cancel(false);
        }
    }
}
