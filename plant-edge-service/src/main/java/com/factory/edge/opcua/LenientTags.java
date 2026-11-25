package com.factory.edge.opcua;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.subscriptions.OpcUaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.subscriptions.OpcUaSubscription;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-tag tolerant initial read and subscription create for the config hot-reload path. A single
 * bad node drops only that node; connection faults still surface to the caller.
 */
final class LenientTags {

    private static final Logger log = LoggerFactory.getLogger(LenientTags.class);

    private LenientTags() {}

    record ReadOutcome(
            List<ParsedTag> accepted, List<OpcUaApply.Reject> rejected, List<TagReading> readings) {

        static ReadOutcome empty() {
            return new ReadOutcome(List.of(), List.of(), List.of());
        }
    }

    record SubscribeOutcome(
            OpcUaSubscription subscription,
            List<ParsedTag> accepted,
            List<OpcUaApply.Reject> rejected) {}

    static ReadOutcome readInitial(OpcUaClient client, List<ParsedTag> tags) throws UaException {
        if (tags.isEmpty()) {
            return ReadOutcome.empty();
        }
        List<NodeId> nodeIds = tags.stream().map(ParsedTag::nodeId).toList();
        Map<NodeId, Boolean> writable = NodeAccessProbe.readWritable(client, nodeIds);
        List<DataValue> values = client.readValues(0.0, TimestampsToReturn.Both, nodeIds);

        List<ParsedTag> accepted = new ArrayList<>(tags.size());
        List<OpcUaApply.Reject> rejected = new ArrayList<>();
        List<TagReading> readings = new ArrayList<>(tags.size());
        for (int i = 0; i < tags.size(); i++) {
            ParsedTag tag = tags.get(i);
            DataValue value = values.get(i);
            TagReading reading =
                    TagReading.from(
                            TagReading.EVENT_READ, tag.config(), value, writable.get(tag.nodeId()));
            StatusCode status = value.getStatusCode();
            if (status != null && status.isBad()) {
                log.warn(
                        "EDGE CONFIG drop tagCode={} nodeId={} status={}",
                        tag.config().getTagCode(),
                        tag.config().getNodeId(),
                        TagReading.symbolicName(status));
                rejected.add(OpcUaApply.Reject.of(tag.config(), OpcUaApply.BAD_INITIAL_READ));
                continue;
            }
            accepted.add(tag);
            readings.add(reading);
        }
        return new ReadOutcome(accepted, rejected, readings);
    }

    /**
     * Creates a parked subscription (publishing disabled) so the caller controls the cutover point.
     * Items whose create status is Bad are rejected instead of failing the whole apply.
     */
    static SubscribeOutcome subscribeLenient(
            OpcUaClient client,
            OpcUaProperties properties,
            List<ParsedTag> tags,
            BooleanSupplier gate,
            Consumer<TagReading> onReading)
            throws UaException {
        OpcUaSubscription subscription =
                new OpcUaSubscription(client, properties.getPublishingIntervalMs());
        subscription.create();
        subscription.setPublishingMode(false);

        Map<NodeId, Boolean> writable =
                NodeAccessProbe.readWritable(client, tags.stream().map(ParsedTag::nodeId).toList());
        List<OpcUaMonitoredItem> items = new ArrayList<>(tags.size());
        for (ParsedTag tag : tags) {
            OpcUaMonitoredItem item = OpcUaMonitoredItem.newDataItem(tag.nodeId());
            item.setSamplingInterval(properties.getPublishingIntervalMs());
            item.setQueueSize(uint(properties.getMonitoredItemQueueSize()));
            Boolean nodeWritable = writable.get(tag.nodeId());
            item.setDataValueListener(
                    (monitoredItem, value) -> {
                        if (!gate.getAsBoolean()) {
                            return;
                        }
                        onReading.accept(
                                TagReading.from(
                                        TagReading.EVENT_CHANGE, tag.config(), value, nodeWritable));
                    });
            items.add(item);
            subscription.addMonitoredItem(item);
        }
        subscription.createMonitoredItems();

        List<ParsedTag> accepted = new ArrayList<>(tags.size());
        List<OpcUaApply.Reject> rejected = new ArrayList<>();
        for (int i = 0; i < tags.size(); i++) {
            StatusCode createStatus = items.get(i).getCreateResult().orElse(StatusCode.BAD);
            if (createStatus.isBad()) {
                log.warn(
                        "EDGE CONFIG drop tagCode={} nodeId={} monitoredItemStatus={}",
                        tags.get(i).config().getTagCode(),
                        tags.get(i).config().getNodeId(),
                        TagReading.symbolicName(createStatus));
                rejected.add(OpcUaApply.Reject.of(tags.get(i).config(), OpcUaApply.BAD_MONITORED_ITEM));
            } else {
                accepted.add(tags.get(i));
            }
        }
        return new SubscribeOutcome(subscription, accepted, rejected);
    }
}
