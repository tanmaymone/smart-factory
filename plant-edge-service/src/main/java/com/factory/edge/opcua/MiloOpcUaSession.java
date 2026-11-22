package com.factory.edge.opcua;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
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
    private LongConsumer onSessionLost;

    public MiloOpcUaSession(OpcUaProperties properties) {
        this.properties = properties;
    }

    @Override
    public synchronized void start(
            List<OpcUaProperties.TagConfig> tags,
            Consumer<TagReading> onReading,
            LongConsumer onSessionLost) {
        this.onSessionLost = onSessionLost;
        sessionLossArmed.set(false);
        sessionLossNotified.set(false);
        long generation = sessionGeneration.incrementAndGet();

        List<ParsedTag> parsed = ParsedTag.parseAll(tags);
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
            MonitoredItemSupport.readInitial(client, parsed, onReading);
            subscription = MonitoredItemSupport.subscribe(client, properties, parsed, onReading);
            sessionLossArmed.set(true);
        } catch (OpcUaProbeException e) {
            close();
            throw e;
        } catch (Exception e) {
            close();
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
        return client != null;
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

    private void requireClient() {
        if (client == null) {
            throw new OpcUaProbeException("OPC UA session unavailable");
        }
    }

    private void closeQuietly() {
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
    }
}
