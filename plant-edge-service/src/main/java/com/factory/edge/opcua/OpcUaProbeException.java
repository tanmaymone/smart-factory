package com.factory.edge.opcua;

public class OpcUaProbeException extends RuntimeException {

    public OpcUaProbeException(String message) {
        super(message);
    }

    public OpcUaProbeException(String message, Throwable cause) {
        super(message, cause);
    }
}
