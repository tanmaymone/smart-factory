package com.factory.edge.opcua;

import com.factory.edge.command.CommandDispatcher;
import com.factory.edge.config.ActiveTagCatalog;
import com.factory.edge.config.EdgeApplyState;
import com.factory.edge.config.EdgeApplyView;
import com.factory.edge.config.EdgeConfigSync;
import com.factory.edge.config.EdgeManifest;
import com.factory.edge.filter.DeadbandFilter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies the central manifest onto the live OPC UA session. Every entry point runs on the single
 * {@code opcua-reconnect} thread, so phase transitions and subscription swaps never interleave.
 */
final class OpcUaReconciler {

    private static final Logger log = LoggerFactory.getLogger(OpcUaReconciler.class);

    private final OpcUaProbe probe;
    private final OpcUaSession session;
    private final EdgeConfigSync sync;
    private final ActiveTagCatalog catalog;
    private final EdgeApplyState applyState;
    private final CommandDispatcher dispatcher;
    private final TagObservationStore observations;

    /** Subscription identity of the last successful apply: tag key to external address. */
    private Map<String, String> appliedAddresses = Map.of();

    private boolean reconnectPass;

    OpcUaReconciler(
            OpcUaProbe probe,
            OpcUaSession session,
            EdgeConfigSync sync,
            ActiveTagCatalog catalog,
            EdgeApplyState applyState,
            CommandDispatcher dispatcher,
            TagObservationStore observations) {
        this.probe = probe;
        this.session = session;
        this.sync = sync;
        this.catalog = catalog;
        this.applyState = applyState;
        this.dispatcher = dispatcher;
        this.observations = observations;
    }

    boolean desiredIsEmpty() {
        return classify(sync.desiredOrNull()).accepted().isEmpty();
    }

    /** Scheduled backoff expiry: re-run the IDLE branch against whatever config is current. */
    void reconnect() {
        reconnectPass = true;
        probe.setPhase(OpcUaProbe.Phase.IDLE);
        reconcile();
    }

    void reconcile() {
        OpcUaProbe.Phase phase = probe.currentPhase();
        if (phase == OpcUaProbe.Phase.STOPPED) {
            return;
        }
        // Read desired once per pass so the manifest and its classification cannot drift apart.
        EdgeManifest desired = sync.desiredOrNull();
        OpcUaApply.Result classified = classify(desired);

        if (classified.accepted().isEmpty()) {
            applyIdle(desired, classified.rejected());
            return;
        }
        if (phase == OpcUaProbe.Phase.BACKOFF) {
            return;
        }
        // Nothing to do only when the exact same manifest is already fully applied and live.
        EdgeApplyView applied = applyState.get();
        if (phase == OpcUaProbe.Phase.UP
                && applied.rejectedCount() == 0
                && desired != null
                && Objects.equals(applied.appliedContentHash(), desired.contentHash())) {
            return;
        }
        if (phase == OpcUaProbe.Phase.IDLE) {
            startFromIdle(desired, classified);
        } else if (phase == OpcUaProbe.Phase.UP) {
            applyWhileUp(desired, classified);
        }
    }

    private static OpcUaApply.Result classify(EdgeManifest desired) {
        return DesiredTagClassifier.classify(desired == null ? List.of() : desired.tags());
    }

    /** No dial-able tag: drop everything, close on purpose, and never schedule a reconnect. */
    private void applyIdle(EdgeManifest desired, List<OpcUaApply.Reject> rejected) {
        boolean wasUp = probe.currentPhase() == OpcUaProbe.Phase.UP;
        reconnectPass = false;
        catalog.clear();
        dispatcher.replaceConfiguredTags(List.of());
        observations.clearAll();
        probe.filter().replaceBands(Map.of());
        probe.filter().clearAll();
        appliedAddresses = Map.of();
        recordApply(desired, List.of(), rejected);
        probe.toIdle(wasUp);
    }

    private void startFromIdle(EdgeManifest desired, OpcUaApply.Result classified) {
        List<OpcUaProperties.TagConfig> candidates = classified.accepted();
        boolean advanceBackoff = reconnectPass;
        reconnectPass = false;
        probe.markStarting();

        DeadbandFilter filter = probe.filter();
        filter.clearAll();
        filter.replaceBands(DeadbandFilter.bandsOf(candidates));
        observations.clearAll();
        // Publish before dialing so each new tag's initial READ is not gated out of the store.
        catalog.publish(candidates);

        OpcUaApply.Result result;
        try {
            session.close();
            result = session.startLenient(candidates, probe.readingSink(), probe.sessionLostSink());
        } catch (RuntimeException e) {
            catalog.clear();
            probe.failToBackoff(e, advanceBackoff);
            return;
        }
        List<OpcUaApply.Reject> rejected = merge(classified.rejected(), result.rejected());
        if (result.accepted().isEmpty()) {
            applyIdle(desired, rejected);
            return;
        }
        publishActive(result.accepted(), keysOf(result.accepted()));
        finishApply(desired, result.accepted(), rejected);
        probe.markUp(result.accepted().size());
    }

    private void applyWhileUp(EdgeManifest desired, OpcUaApply.Result classified) {
        List<OpcUaProperties.TagConfig> candidates = classified.accepted();
        Map<String, String> desiredAddresses = addressesOf(candidates);
        Set<String> reseed = new HashSet<>();
        desiredAddresses.forEach(
                (key, address) -> {
                    if (!address.equals(appliedAddresses.get(key))) {
                        reseed.add(key);
                    }
                });

        if (desiredAddresses.equals(appliedAddresses)) {
            // Deadband / writable only: the live subscription already watches the right nodes.
            publishActive(candidates, Set.of());
            finishApply(desired, candidates, classified.rejected());
            return;
        }

        OpcUaSession.SwapPlan plan;
        try {
            plan = session.prepareSwap(candidates, reseed, probe.readingSink());
        } catch (RuntimeException e) {
            probe.failToBackoff(e, false);
            return;
        }
        List<OpcUaApply.Reject> rejected = merge(classified.rejected(), plan.rejected());
        if (plan.accepted().isEmpty()) {
            session.abortSwap(plan);
            applyIdle(desired, rejected);
            return;
        }

        Set<String> acceptedKeys = keysOf(plan.accepted());
        Set<String> evict = new HashSet<>(reseed);
        appliedAddresses.keySet().stream().filter(key -> !acceptedKeys.contains(key)).forEach(evict::add);

        session.deactivateReadings();
        publishActive(plan.accepted(), evict);
        session.commitSwap(plan);
        finishApply(desired, plan.accepted(), rejected);
    }

    private void publishActive(List<OpcUaProperties.TagConfig> accepted, Set<String> evict) {
        catalog.publish(accepted);
        observations.prune(keysOf(accepted));
        DeadbandFilter filter = probe.filter();
        filter.replaceBands(DeadbandFilter.bandsOf(accepted));
        filter.evict(evict);
    }

    private void finishApply(
            EdgeManifest desired,
            List<OpcUaProperties.TagConfig> accepted,
            List<OpcUaApply.Reject> rejected) {
        dispatcher.replaceConfiguredTags(accepted);
        appliedAddresses = addressesOf(accepted);
        recordApply(desired, accepted, rejected);
        log.info(
                "EDGE CONFIG applied version={} activeTags={} rejected={}",
                desired == null ? null : desired.version(),
                accepted.size(),
                rejected.size());
    }

    private void recordApply(
            EdgeManifest desired,
            List<OpcUaProperties.TagConfig> accepted,
            List<OpcUaApply.Reject> rejected) {
        List<EdgeApplyView.RejectedTag> rows =
                rejected.stream()
                        .map(
                                r ->
                                        new EdgeApplyView.RejectedTag(
                                                r.lineId(), r.tagCode(), r.externalAddress(), r.reason()))
                        .toList();
        applyState.set(
                new EdgeApplyView(
                        desired == null ? null : desired.version(),
                        desired == null ? null : desired.contentHash(),
                        Instant.now(),
                        rows,
                        rows.size(),
                        accepted.size()));
    }

    private static List<OpcUaApply.Reject> merge(
            List<OpcUaApply.Reject> first, List<OpcUaApply.Reject> second) {
        List<OpcUaApply.Reject> all = new ArrayList<>(first);
        all.addAll(second);
        return List.copyOf(all);
    }

    private static Set<String> keysOf(List<OpcUaProperties.TagConfig> tags) {
        Set<String> keys = new HashSet<>();
        tags.forEach(tag -> keys.add(DesiredTagClassifier.keyOf(tag)));
        return keys;
    }

    private static Map<String, String> addressesOf(List<OpcUaProperties.TagConfig> tags) {
        Map<String, String> addresses = new HashMap<>();
        tags.forEach(tag -> addresses.put(DesiredTagClassifier.keyOf(tag), tag.getNodeId()));
        return Map.copyOf(addresses);
    }
}
