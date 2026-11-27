package com.factory.central.registry;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SiteRequest(
        @NotBlank @Size(max = 64) String siteCode,
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 64) String timezone,
        RegistryLifecycle lifecycle,
        String siteLabel,
        String lineLabel) {}
