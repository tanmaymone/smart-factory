package com.factory.edge.opcua;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.stream.Collectors;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.SessionActivityListener;
import org.eclipse.milo.opcua.sdk.client.UaSession;
import org.eclipse.milo.opcua.sdk.client.subscriptions.OpcUaSubscription;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MiloOpcUaSession implements OpcUaSession {

    private static final Logger log = LoggerFactory.getLogger(MiloOpcUaSession.class);

    private final OpcUaProperties properties;
    private final AtomicBoolean sessionLossArmed = new AtomicBoolean(false);
    private final AtomicBoolean sessionLossNotified = new AtomicBoolean(false);
    private final AtomicLong sessionGeneration = new AtomicLong(0);
    private final AtomicReference<Object> activeReadingToken = new AtomicReference<>();

    private OpcUaClient client;
    private OpcUaSubscription subscription;
    private Map<String, UShort> namespaceIndexByUri = Map.of();
    private LongConsumer onSessionLost;

    public MiloOpcUaSession(OpcUaProperties properties) {
        this.properties = properties;
    }

    @Override
    public synchronized void start(
            List<OpcUaProperties.TagConfig> tags,
            Consumer<TagReading> onReading,
            LongConsumer onSessionLost) {
        // Syntax-check plant addresses before discovery so bad config fails fast without BACKOFF.
        List<NodeAddress> addresses = NamespaceResolver.parseAll(tags);
        connect(onSessionLost);
        try {
            namespaceIndexByUri = NamespaceResolver.readTable(client);
            List<ParsedTag> parsed = NamespaceResolver.resolveAll(namespaceIndexByUri, tags, addresses);
            MonitoredItemSupport.readInitial(client, parsed, onReading);
            subscription = MonitoredItemSupport.subscribe(client, properties, parsed, onReading);
            activeReadingToken.set(new Object());
            sessionLossArmed.set(true);
        } catch (OpcUaProbeException | IllegalStateException e) {
            closeQuietly();
            throw e;
        } catch (Exception e) {
            closeQuietly();
            throw wrap(e);
        }
    }

    @Override
    public synchronized OpcUaApply.Result startLenient(
            List<OpcUaProperties.TagConfig> tags,
            Consumer<TagReading> onReading,
            LongConsumer onSessionLost) {
        connect(onSessionLost);
        try {
            namespaceIndexByUri = NamespaceResolver.readTable(client);
            Object token = new Object();
            MiloSwapPlan plan = buildPlan(tags, null, onReading, token);
            if (plan.accepted().isEmpty()) {
                deletePlanSubscription(plan);
                closeQuietly();
                return new OpcUaApply.Result(List.of(), plan.rejected());
            }
            activeReadingToken.set(token);
            plan.readings().forEach(onReading);
            plan.subscription().setPublishingMode(true);
            subscription = plan.subscription();
            sessionLossArmed.set(true);
            return new OpcUaApply.Result(plan.accepted(), plan.rejected());
        } catch (OpcUaProbeException | IllegalStateException e) {
            closeQuietly();
            throw e;
        } catch (Exception e) {
            closeQuietly();
            throw wrap(e);
        }
    }

    @Override
    public synchronized SwapPlan prepareSwap(
            List<OpcUaProperties.TagConfig> tags,
            Set<String> initialReadKeys,
            Consumer<TagReading> onReading) {
        requireClient();
        try {
            // One NamespaceArray refresh per apply pass so re-indexed servers resolve correctly.
            namespaceIndexByUri = NamespaceResolver.readTable(client);
            return buildPlan(tags, initialReadKeys, onReading, new Object());
        } catch (OpcUaProbeException e) {
            throw e;
        } catch (Exception e) {
            throw wrap(e);
        }
    }

    @Override
    public synchronized void deactivateReadings() {
        activeReadingToken.set(null);
    }

    @Override
    public synchronized void commitSwap(SwapPlan plan) {
        MiloSwapPlan next = (MiloSwapPlan) plan;
        OpcUaSubscription previous = subscription;
        subscription = next.subscription();
        activeReadingToken.set(next.token());
        next.readings().forEach(next.onReading());
        try {
            next.subscription().setPublishingMode(true);
        } catch (Exception e) {
            throw wrap(e);
        }
        deleteQuietly(previous);
    }

    @Override
    public synchronized void abortSwap(SwapPlan plan) {
        deletePlanSubscription((MiloSwapPlan) plan);
    }

    @Override
    public synchronized boolean isAvailable() {
        return client != null && !namespaceIndexByUri.isEmpty();
    }

    @Override
    public synchronized NodeId resolve(String nodeIdText) {
        requireClient();
        return NamespaceResolver.toNodeId(namespaceIndexByUri, NodeIdParser.parse(nodeIdText));
    }

    @Override
    public synchronized DataValue readAttribute(NodeId nodeId, AttributeId attributeId) {
        requireClient();
        try {
            ReadValueId id =
                    new ReadValueId(nodeId, attributeId.uid(), null, QualifiedName.NULL_VALUE);
            return client.read(0.0, TimestampsToReturn.Both, List.of(id)).getResults()[0];
        } catch (Exception e) {
            throw new OpcUaProbeException(
                    "OPC UA readAttribute failed nodeId=" + nodeId + ": " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized DataValue readValue(NodeId nodeId) {
        requireClient();
        try {
            return client.readValue(0.0, TimestampsToReturn.Both, nodeId);
        } catch (Exception e) {
            throw new OpcUaProbeException(
                    "OPC UA readValue failed nodeId=" + nodeId + ": " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized StatusCode writeValue(NodeId nodeId, Variant value) {
        requireClient();
        try {
            DataValue dataValue = new DataValue(value, StatusCode.GOOD, null);
            return client.writeValues(List.of(nodeId), List.of(dataValue)).get(0);
        } catch (Exception e) {
            throw new OpcUaProbeException(
                    "OPC UA writeValue failed nodeId=" + nodeId + ": " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void close() {
        sessionLossArmed.set(false);
        sessionGeneration.incrementAndGet();
        closeQuietly();
    }

    /** Drops any prior client, map, and listener before connecting so a second start cannot leak. */
    private void connect(LongConsumer lostCallback) {
        closeQuietly();
        this.onSessionLost = lostCallback;
        sessionLossArmed.set(false);
        sessionLossNotified.set(false);
        long generation = sessionGeneration.incrementAndGet();
        try {
            client = OpcUaClientFactory.create(properties);
            client.addSessionActivityListener(
                    new SessionActivityListener() {
                        @Override
                        public void onSessionInactive(UaSession session) {
                            notifyLost(generation);
                        }
                    });
            OpcUaClientFactory.connect(client, properties);
        } catch (OpcUaProbeException | IllegalStateException e) {
            closeQuietly();
            throw e;
        } catch (Exception e) {
            closeQuietly();
            throw wrap(e);
        }
    }

    private void notifyLost(long generation) {
        if (sessionLossArmed.get()
                && sessionGeneration.get() == generation
                && sessionLossNotified.compareAndSet(false, true)) {
            LongConsumer callback = this.onSessionLost;
            if (callback != null) {
                callback.accept(generation);
            }
        }
    }

    private MiloSwapPlan buildPlan(
            List<OpcUaProperties.TagConfig> tags,
            Set<String> initialReadKeys,
            Consumer<TagReading> onReading,
            Object token)
            throws Exception {
        NamespaceResolver.Lenient resolved =
                NamespaceResolver.resolveLenient(namespaceIndexByUri, tags);
        List<OpcUaApply.Reject> rejected = new ArrayList<>(resolved.rejected());

        List<ParsedTag> toRead = filterForInitialRead(resolved.accepted(), initialReadKeys);
        LenientTags.ReadOutcome read = LenientTags.readInitial(client, toRead);
        rejected.addAll(read.rejected());

        Set<String> readRejects = rejectKeys(read.rejected());
        List<ParsedTag> candidates =
                resolved.accepted().stream()
                        .filter(tag -> !readRejects.contains(rejectKey(tag.config())))
                        .toList();

        LenientTags.SubscribeOutcome subscribed =
                LenientTags.subscribeLenient(
                        client,
                        properties,
                        candidates,
                        () -> activeReadingToken.get() == token,
                        onReading);
        rejected.addAll(subscribed.rejected());

        Set<String> itemRejects = rejectKeys(subscribed.rejected());
        List<TagReading> readings =
                read.readings().stream()
                        .filter(r -> !itemRejects.contains(r.lineId() + "|" + r.tagCode()))
                        .toList();
        List<OpcUaProperties.TagConfig> accepted =
                subscribed.accepted().stream().map(ParsedTag::config).toList();
        return new MiloSwapPlan(
                subscribed.subscription(), token, accepted, List.copyOf(rejected), readings, onReading);
    }

    private static List<ParsedTag> filterForInitialRead(
            List<ParsedTag> tags, Set<String> initialReadKeys) {
        if (initialReadKeys == null) {
            return tags;
        }
        return tags.stream()
                .filter(tag -> initialReadKeys.contains(DesiredTagClassifier.keyOf(tag.config())))
                .toList();
    }

    // One edge process serves one site, so line|tag uniquely identifies a reject within an apply.
    private static Set<String> rejectKeys(List<OpcUaApply.Reject> rejects) {
        return rejects.stream().map(r -> r.lineId() + "|" + r.tagCode()).collect(Collectors.toSet());
    }

    private static String rejectKey(OpcUaProperties.TagConfig tag) {
        return tag.getLineId() + "|" + tag.getTagCode();
    }

    private void deletePlanSubscription(MiloSwapPlan plan) {
        deleteQuietly(plan.subscription());
    }

    private static void deleteQuietly(OpcUaSubscription target) {
        if (target == null) {
            return;
        }
        try {
            target.delete();
        } catch (Exception e) {
            log.debug("Subscription delete: {}", e.toString());
        }
    }

    private OpcUaProbeException wrap(Exception cause) {
        return new OpcUaProbeException(
                "OPC UA session start failed for "
                        + properties.getEndpointUrl()
                        + ": "
                        + cause.getMessage(),
                cause);
    }

    private void requireClient() {
        if (client == null) {
            throw new OpcUaProbeException("OPC UA session unavailable");
        }
    }

    private void closeQuietly() {
        sessionLossArmed.set(false);
        activeReadingToken.set(null);
        namespaceIndexByUri = Map.of();
        deleteQuietly(subscription);
        subscription = null;
        if (client != null) {
            try {
                client.disconnect();
            } catch (Exception e) {
                log.debug("Client disconnect during cleanup: {}", e.toString());
            }
            client = null;
        }
        onSessionLost = null;
    }

    private record MiloSwapPlan(
            OpcUaSubscription subscription,
            Object token,
            List<OpcUaProperties.TagConfig> accepted,
            List<OpcUaApply.Reject> rejected,
            List<TagReading> readings,
            Consumer<TagReading> onReading)
            implements SwapPlan {}
}
