package com.factory.central.access;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(max = 255) String actorId, @NotBlank @Size(max = 255) String password) {}