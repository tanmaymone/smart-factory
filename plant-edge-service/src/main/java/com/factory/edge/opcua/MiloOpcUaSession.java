package com.factory.edge.opcua;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.SessionActivityListener;
import org.eclipse.milo.opcua.sdk.client.UaSession;
import org.eclipse.milo.opcua.sdk.client.subscriptions.OpcUaSubscription;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.NodeIds;
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
        // Drop any prior client, map, and listener before connecting so a second start cannot leak.
        closeQuietly();
        this.onSessionLost = onSessionLost;
        sessionLossArmed.set(false);
        sessionLossNotified.set(false);
        long generation = sessionGeneration.incrementAndGet();

        // Syntax-check plant addresses before discovery so bad config fails fast without BACKOFF.
        List<NodeAddress> addresses = parseAddresses(tags);

        try {
            client = OpcUaClientFactory.create(properties);
            client.addSessionActivityListener(
                    new SessionActivityListener() {
                        @Override
                        public void onSessionInactive(UaSession session) {
                            if (sessionLossArmed.get()
                                    && sessionGeneration.get() == generation
                                    && sessionLossNotified.compareAndSet(false, true)) {
                                LongConsumer callback = MiloOpcUaSession.this.onSessionLost;
                                if (callback != null) {
                                    callback.accept(generation);
                                }
                            }
                        }
                    });
            OpcUaClientFactory.connect(client, properties);
            namespaceIndexByUri = readNamespaceTable(client);
            List<ParsedTag> parsed = resolveTags(tags, addresses);
            MonitoredItemSupport.readInitial(client, parsed, onReading);
            subscription = MonitoredItemSupport.subscribe(client, properties, parsed, onReading);
            sessionLossArmed.set(true);
        } catch (OpcUaProbeException | IllegalStateException e) {
            closeQuietly();
            throw e;
        } catch (Exception e) {
            closeQuietly();
            throw new OpcUaProbeException(
                    "OPC UA session start failed for "
                            + properties.getEndpointUrl()
                            + ": "
                            + e.getMessage(),
                    e);
        }
    }

    @Override
    public synchronized boolean isAvailable() {
        return client != null && !namespaceIndexByUri.isEmpty();
    }

    @Override
    public synchronized NodeId resolve(String nodeIdText) {
        requireClient();
        return resolveUnlocked(nodeIdText);
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

    private static List<NodeAddress> parseAddresses(List<OpcUaProperties.TagConfig> tags) {
        List<NodeAddress> addresses = new java.util.ArrayList<>(tags.size());
        for (OpcUaProperties.TagConfig tag : tags) {
            try {
                addresses.add(NodeIdParser.parse(tag.getNodeId()));
            } catch (OpcUaProbeException e) {
                throw new OpcUaProbeException(
                        "Invalid NodeId for siteId="
                                + tag.getSiteId()
                                + " lineId="
                                + tag.getLineId()
                                + " tagCode="
                                + tag.getTagCode()
                                + " nodeId="
                                + tag.getNodeId()
                                + ": "
                                + e.getMessage(),
                        e);
            }
        }
        return addresses;
    }

    private List<ParsedTag> resolveTags(
            List<OpcUaProperties.TagConfig> tags, List<NodeAddress> addresses) {
        List<ParsedTag> parsed = new java.util.ArrayList<>(tags.size());
        for (int i = 0; i < tags.size(); i++) {
            OpcUaProperties.TagConfig tag = tags.get(i);
            try {
                parsed.add(new ParsedTag(tag, toNodeId(addresses.get(i))));
            } catch (OpcUaProbeException e) {
                throw new OpcUaProbeException(
                        "Invalid NodeId for siteId="
                                + tag.getSiteId()
                                + " lineId="
                                + tag.getLineId()
                                + " tagCode="
                                + tag.getTagCode()
                                + " nodeId="
                                + tag.getNodeId()
                                + ": "
                                + e.getMessage(),
                        e);
            }
        }
        return parsed;
    }

    private NodeId resolveUnlocked(String nodeIdText) {
        return toNodeId(NodeIdParser.parse(nodeIdText));
    }

    private NodeId toNodeId(NodeAddress address) {
        return switch (address) {
            case NodeAddress.Resolved r -> r.nodeId();
            case NodeAddress.Unresolved u -> {
                UShort index = namespaceIndexByUri.get(u.namespaceUri());
                if (index == null) {
                    throw new OpcUaProbeException(
                            "Unknown namespace URI (not in Server_NamespaceArray): "
                                    + u.namespaceUri());
                }
                yield u.identifier().toNodeId(index);
            }
        };
    }

    private Map<String, UShort> readNamespaceTable(OpcUaClient liveClient) {
        try {
            DataValue value =
                    liveClient.readValue(0.0, TimestampsToReturn.Both, NodeIds.Server_NamespaceArray);
            if (!QualityGate.requireGood(value)) {
                StatusCode status = value == null ? null : value.getStatusCode();
                throw new OpcUaProbeException(
                        "Server_NamespaceArray quality is not GOOD status="
                                + (status == null ? "null" : TagReading.symbolicName(status)));
            }
            Variant variant = value.getValue();
            if (variant == null || variant.isNull() || !(variant.getValue() instanceof String[] uris)) {
                throw new OpcUaProbeException("Server_NamespaceArray value is missing or not String[]");
            }
            Map<String, UShort> map = new HashMap<>();
            for (int i = 0; i < uris.length; i++) {
                if (uris[i] != null && !uris[i].isBlank()) {
                    map.put(uris[i], UShort.valueOf(i));
                }
            }
            if (map.isEmpty()) {
                throw new OpcUaProbeException("Server_NamespaceArray is empty");
            }
            return Map.copyOf(map);
        } catch (OpcUaProbeException e) {
            throw e;
        } catch (Exception e) {
            throw new OpcUaProbeException(
                    "Failed to read Server_NamespaceArray: " + e.getMessage(), e);
        }
    }

    private void requireClient() {
        if (client == null) {
            throw new OpcUaProbeException("OPC UA session unavailable");
        }
    }

    private void closeQuietly() {
        sessionLossArmed.set(false);
        namespaceIndexByUri = Map.of();
        if (subscription != null) {
            try {
                subscription.delete();
            } catch (Exception e) {
                log.debug("Subscription delete during cleanup: {}", e.toString());
            }
            subscription = null;
        }
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
}
