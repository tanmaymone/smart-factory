package com.factory.edge.opcua;

import java.util.List;
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

    /** True when a live client is connected and ready for read/write. */
    boolean isAvailable();

    DataValue readAttribute(NodeId nodeId, AttributeId attributeId);

    DataValue readValue(NodeId nodeId);

    StatusCode writeValue(NodeId nodeId, Variant value);

    @Override
    void close();
}
