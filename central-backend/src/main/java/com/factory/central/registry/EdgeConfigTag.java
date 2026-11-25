package com.factory.central.registry;

public record EdgeConfigTag(
        String tagCode,
        String protocol,
        String externalAddress,
        String dataType,
        boolean writable,
        double deadband,
        String endpointUrl) {}
