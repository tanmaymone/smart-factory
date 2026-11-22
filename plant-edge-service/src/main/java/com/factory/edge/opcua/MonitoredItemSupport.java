package com.factory.edge.opcua;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

import java.util.List;
import java.util.function.Consumer;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.subscriptions.MonitoredItemSynchronizationException;
import org.eclipse.milo.opcua.sdk.client.subscriptions.OpcUaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.subscriptions.OpcUaSubscription;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Initial read and CHANGE subscription helpers for {@link MiloOpcUaSession}. */
final class MonitoredItemSupport {

    private static final Logger log = LoggerFactory.getLogger(MonitoredItemSupport.class);

    private MonitoredItemSupport() {}

    static void readInitial(
            OpcUaClient client, List<ParsedTag> tags, Consumer<TagReading> onReading)
            throws UaException {
        List<NodeId> nodeIds = tags.stream().map(ParsedTag::nodeId).toList();
        List<DataValue> values = client.readValues(0.0, TimestampsToReturn.Both, nodeIds);

        OpcUaProbeException failure = null;
        for (int i = 0; i < tags.size(); i++) {
            ParsedTag tag = tags.get(i);
            DataValue value = values.get(i);
            TagReading reading = TagReading.from(TagReading.EVENT_READ, tag.config(), value);
            StatusCode status = value.getStatusCode();
            if (status != null && status.isBad()) {
                log.error(TagReadingFormatter.format(reading));
                if (failure == null) {
                    failure =
                            new OpcUaProbeException(
                                    "Initial read Bad for siteId="
                                            + tag.config().getSiteId()
                                            + " lineId="
                                            + tag.config().getLineId()
                                            + " tagCode="
                                            + tag.config().getTagCode()
                                            + " nodeId="
                                            + tag.config().getNodeId()
                                            + " status="
                                            + TagReading.symbolicName(status));
                }
            } else {
                onReading.accept(reading);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    static OpcUaSubscription subscribe(
            OpcUaClient client,
            OpcUaProperties properties,
            List<ParsedTag> tags,
            Consumer<TagReading> onReading)
            throws UaException {
        OpcUaSubscription subscription =
                new OpcUaSubscription(client, properties.getPublishingIntervalMs());
        subscription.create();

        for (ParsedTag tag : tags) {
            OpcUaMonitoredItem item = OpcUaMonitoredItem.newDataItem(tag.nodeId());
            item.setSamplingInterval(properties.getPublishingIntervalMs());
            item.setQueueSize(uint(properties.getMonitoredItemQueueSize()));
            item.setDataValueListener(
                    (monitoredItem, value) ->
                            onReading.accept(
                                    TagReading.from(TagReading.EVENT_CHANGE, tag.config(), value)));
            subscription.addMonitoredItem(item);
        }

        try {
            subscription.synchronizeMonitoredItems();
        } catch (MonitoredItemSynchronizationException e) {
            throw new OpcUaProbeException(
                    "Monitored item create failed for "
                            + properties.getEndpointUrl()
                            + ": "
                            + e.getMessage(),
                    e);
        }

        for (OpcUaMonitoredItem item : subscription.getMonitoredItems()) {
            StatusCode createStatus = item.getCreateResult().orElse(StatusCode.BAD);
            if (createStatus.isBad()) {
                throw badMonitoredItem(tags, item, createStatus);
            }
        }
        return subscription;
    }

    private static OpcUaProbeException badMonitoredItem(
            List<ParsedTag> tags, OpcUaMonitoredItem item, StatusCode createStatus) {
        ParsedTag tag = findTag(tags, item);
        return new OpcUaProbeException(
                "Monitored item Bad for siteId="
                        + (tag == null ? "null" : tag.config().getSiteId())
                        + " lineId="
                        + (tag == null ? "null" : tag.config().getLineId())
                        + " tagCode="
                        + (tag == null ? "null" : tag.config().getTagCode())
                        + " nodeId="
                        + (tag == null
                                ? item.getReadValueId().getNodeId()
                                : tag.config().getNodeId())
                        + " status="
                        + TagReading.symbolicName(createStatus));
    }

    private static ParsedTag findTag(List<ParsedTag> tags, OpcUaMonitoredItem item) {
        NodeId nodeId = item.getReadValueId().getNodeId();
        for (ParsedTag tag : tags) {
            if (tag.nodeId().equals(nodeId)) {
                return tag;
            }
        }
        return null;
    }
}
