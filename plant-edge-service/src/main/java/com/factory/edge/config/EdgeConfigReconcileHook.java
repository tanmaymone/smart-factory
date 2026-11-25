package com.factory.edge.config;

/** Posted onto opcua-reconnect; implemented by OpcUaProbe. */
@FunctionalInterface
public interface EdgeConfigReconcileHook {
    void requestReconcile();
}
