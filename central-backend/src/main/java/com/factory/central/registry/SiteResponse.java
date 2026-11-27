package com.factory.central.registry;

public record SiteResponse(
        String siteCode,
        String name,
        String timezone,
        SiteStatus status,
        RegistryLifecycle lifecycle,
        String siteLabel,
        String lineLabel) {

    public static SiteResponse from(Site site) {
        return new SiteResponse(
                site.getId(),
                site.getName(),
                site.getTimezone(),
                site.getStatus(),
                site.getLifecycle(),
                site.getSiteLabel(),
                site.getLineLabel());
    }
}
