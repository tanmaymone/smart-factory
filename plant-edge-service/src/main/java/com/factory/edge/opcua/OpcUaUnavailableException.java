package com.factory.edge.opcua;

/** OPC UA server unreachable (connection refused, timeout). Probe may retry. */
public class OpcUaUnavailableException extends OpcUaProbeException {

    public OpcUaUnavailableException(String message) {
        super(message);
    }

    public OpcUaUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
