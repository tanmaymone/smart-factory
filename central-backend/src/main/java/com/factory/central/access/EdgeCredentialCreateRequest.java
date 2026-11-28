package com.factory.central.access;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EdgeCredentialCreateRequest(
        @NotBlank @Size(max = 64) String siteId, @NotBlank @Size(max = 128) String label) {}