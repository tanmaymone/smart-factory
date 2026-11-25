package com.factory.edge.opcua;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;

/**
 * OPC UA session used by the probe and command writer. Implementations must disconnect before
 * throwing so Netty threads cannot keep the JVM alive after a failed start.
 */
public interface OpcUaSession extends AutoCloseable {

    /**
     * @param onSessionLost invoked with the session generation that was lost (never on the Milo
     *     callback thread's critical path beyond notification)
     */
    void start(
            List<OpcUaProperties.TagConfig> tags,
            Consumer<TagReading> onReading,
            LongConsumer onSessionLost);

    /**
     * Connect once and subscribe whatever the server accepts. A dead endpoint still throws {@link
     * OpcUaUnavailableException}; per-tag faults land in the result's rejected list instead.
     */
    OpcUaApply.Result startLenient(
            List<OpcUaProperties.TagConfig> tags,
            Consumer<TagReading> onReading,
            LongConsumer onSessionLost);

    /**
     * Builds a parked (publishing-disabled) replacement subscription on the live connection so the
     * caller owns the cutover point. {@code initialReadKeys} ({@code siteId|lineId|tagCode}) limits
     * the initial READ to new or re-addressed tags; those readings stay buffered until commit.
     */
    SwapPlan prepareSwap(
            List<OpcUaProperties.TagConfig> tags,
            Set<String> initialReadKeys,
            Consumer<TagReading> onReading);

    /** Stops readings from the subscription currently in place, before the new one goes live. */
    void deactivateReadings();

    /** Flushes buffered READs, enables publishing on the plan, then deletes the old subscription. */
    void commitSwap(SwapPlan plan);

    /** Drops the parked subscription and leaves the live one untouched. */
    void abortSwap(SwapPlan plan);

    /** True when a live client is connected and ready for read/write. */
    boolean isAvailable();

    /**
     * Resolve a plant address ({@code nsu=} or {@code ns=0}) against the session NamespaceArray
     * cache. Throws when the session is down or the URI is unknown.
     */
    NodeId resolve(String nodeIdText);

    DataValue readAttribute(NodeId nodeId, AttributeId attributeId);

    DataValue readValue(NodeId nodeId);

    StatusCode writeValue(NodeId nodeId, Variant value);

    @Override
    void close();

    /** Opaque parked-subscription handle produced by {@link #prepareSwap}. */
    interface SwapPlan {

        List<OpcUaProperties.TagConfig> accepted();

        List<OpcUaApply.Reject> rejected();
    }
}
