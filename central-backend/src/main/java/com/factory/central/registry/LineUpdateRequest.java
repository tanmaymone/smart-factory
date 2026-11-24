package com.factory.central.registry;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LineUpdateRequest(
        @NotBlank @Size(max = 255) String name, RegistryLifecycle lifecycle) {}
