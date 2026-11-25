package com.factory.edge.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * HTTP poll + snapshot owner. Writes {@link EdgeSyncView} only; never writes appliedVersion.
 * Sync stays inert when {@code app.edge.config-sync-enabled=false}.
 */
@Component
public class EdgeConfigSync {

    private static final Logger log = LoggerFactory.getLogger(EdgeConfigSync.class);

    private final EdgeConfigProperties properties;
    private final EdgeConfigClient client;
    private final EdgeConfigSnapshotStore snapshotStore;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<EdgeConfigReconcileHook> reconcileHook;
    private final AtomicReference<EdgeManifest> desired = new AtomicReference<>();
    private final AtomicReference<EdgeSyncView> syncView = new AtomicReference<>(EdgeSyncView.empty());
    private final AtomicBoolean started = new AtomicBoolean();
    private final ScheduledExecutorService poller =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "edge-config-sync");
                t.setDaemon(true);
                return t;
            });

    public EdgeConfigSync(
            EdgeConfigProperties properties,
            EdgeConfigClient client,
            EdgeConfigSnapshotStore snapshotStore,
            ObjectMapper objectMapper,
            ObjectProvider<EdgeConfigReconcileHook> reconcileHook) {
        this.properties = properties;
        this.client = client;
        this.snapshotStore = snapshotStore;
        this.objectMapper = objectMapper;
        this.reconcileHook = reconcileHook;
    }

    public AtomicReference<EdgeManifest> desiredRef() {
        return desired;
    }

    public EdgeSyncView syncView() {
        return syncView.get();
    }

    public EdgeManifest desiredOrNull() {
        return desired.get();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!properties.isConfigSyncEnabled()) {
            syncView.set(EdgeSyncView.empty().withSource(EdgeSyncView.SOURCE_STATIC_YAML));
            log.info("EDGE CONFIG sync disabled; using static YAML tags");
            return;
        }
        if (!started.compareAndSet(false, true)) {
            return;
        }
        poller.execute(this::bootstrap);
        poller.scheduleWithFixedDelay(
                this::syncOnceSafe,
                properties.getConfigPollMs(),
                properties.getConfigPollMs(),
                TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void shutdown() {
        poller.shutdownNow();
    }

    /** Package-visible for tests — does not block Spring start. */
    void bootstrap() {
        Optional<EdgeConfigSnapshotStore.LoadedSnapshot> loaded = snapshotStore.read();
        if (loaded.isPresent()) {
            EdgeManifest manifest = loaded.get().manifest();
            Instant savedAt = loaded.get().savedAt();
            desired.set(manifest);
            syncView.set(new EdgeSyncView(
                    manifest.version(),
                    manifest.contentHash(),
                    EdgeSyncView.SOURCE_LOCAL_SNAPSHOT,
                    savedAt,
                    Instant.now(),
                    null));
            requestReconcile();
        }
        syncOnce();
    }

    void syncOnceSafe() {
        try {
            syncOnce();
        } catch (Exception ex) {
            log.warn("EDGE CONFIG poll failed: {}", ex.getMessage());
        }
    }

    void syncOnce() {
        if (!properties.isConfigSyncEnabled()) {
            return;
        }
        Instant attemptAt = Instant.now();
        EdgeSyncView current = syncView.get();
        Long fetched = current.fetchedVersion();
        EdgeConfigClient.FetchResult result = client.fetch(fetched);
        switch (result) {
            case EdgeConfigClient.FetchResult.NotModified ignored -> {
                EdgeSyncView next = current;
                if (EdgeSyncView.SOURCE_LOCAL_SNAPSHOT.equals(current.source())) {
                    next = current.withSource(EdgeSyncView.SOURCE_CENTRAL);
                }
                syncView.set(new EdgeSyncView(
                        next.fetchedVersion(),
                        next.contentHash(),
                        next.source(),
                        next.lastSyncedAt(),
                        attemptAt,
                        null));
            }
            case EdgeConfigClient.FetchResult.Ok ok -> applyCentralBody(ok.rawBody(), attemptAt);
            case EdgeConfigClient.FetchResult.NotFound ignored -> {
                syncView.set(current.withError("SITE_NOT_FOUND", attemptAt));
                if (current.fetchedVersion() == null && desired.get() == null) {
                    syncView.set(new EdgeSyncView(
                            null, null, EdgeSyncView.SOURCE_NONE, null, attemptAt, "SITE_NOT_FOUND"));
                }
            }
            case EdgeConfigClient.FetchResult.Error err ->
                    syncView.set(current.withError(err.message(), attemptAt));
        }
    }

    private void applyCentralBody(String rawBody, Instant attemptAt) {
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            EdgeManifest parsed = EdgeManifest.parse(rawBody, root);
            if (parsed.version() < 1
                    || parsed.contentHash() == null
                    || parsed.contentHash().isBlank()
                    || !properties.getNormalizedSiteId().equals(parsed.siteId().trim())) {
                syncView.set(syncView.get().withError("invalid manifest body", attemptAt));
                return;
            }
            // Ensure lines field present (null fails validation).
            if (root.get("lines") == null || !root.get("lines").isArray()) {
                syncView.set(syncView.get().withError("invalid manifest lines", attemptAt));
                return;
            }
            snapshotStore.write(parsed);
            desired.set(parsed);
            syncView.set(new EdgeSyncView(
                    parsed.version(),
                    parsed.contentHash(),
                    EdgeSyncView.SOURCE_CENTRAL,
                    Instant.now(),
                    attemptAt,
                    null));
            requestReconcile();
        } catch (Exception ex) {
            syncView.set(syncView.get().withError(ex.getMessage(), attemptAt));
            log.warn("EDGE CONFIG apply failed: {}", ex.getMessage());
        }
    }

    private void requestReconcile() {
        EdgeConfigReconcileHook hook = reconcileHook.getIfAvailable();
        if (hook != null) {
            hook.requestReconcile();
        }
    }
}
