package com.factory.central.registry;

public final class RegistryLiveness {

    private RegistryLiveness() {}

    public static boolean isLive(Site site, Line line, TagMapping tag) {
        return site != null
                && line != null
                && tag != null
                && site.getLifecycle() == RegistryLifecycle.ACTIVE
                && line.getLifecycle() == RegistryLifecycle.ACTIVE
                && tag.getLifecycle() == RegistryLifecycle.ACTIVE;
    }
}
