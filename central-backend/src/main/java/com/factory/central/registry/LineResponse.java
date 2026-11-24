package com.factory.central.registry;

public record LineResponse(String siteCode, String lineCode, String name, RegistryLifecycle lifecycle) {

    public static LineResponse from(Line line) {
        return new LineResponse(line.getSiteId(), line.getLineId(), line.getName(), line.getLifecycle());
    }
}
