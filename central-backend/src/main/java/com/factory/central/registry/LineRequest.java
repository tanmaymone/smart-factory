package com.factory.central.registry;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LineRequest(
        @NotBlank @Size(max = 64) String siteCode,
        @NotBlank @Size(max = 64) String lineCode,
        @NotBlank @Size(max = 255) String name,
        RegistryLifecycle lifecycle) {}
