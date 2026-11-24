package com.factory.central.registry;

public record SiteResponse(
        String siteCode, String name, String timezone, SiteStatus status, RegistryLifecycle lifecycle) {

    public static SiteResponse from(Site site) {
        return new SiteResponse(
                site.getId(), site.getName(), site.getTimezone(), site.getStatus(), site.getLifecycle());
    }
}
