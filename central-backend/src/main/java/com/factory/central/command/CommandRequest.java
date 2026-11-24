package com.factory.central.command;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CommandRequest(
        @NotBlank @Size(max = 64) String siteId,
        @NotBlank @Size(max = 64) String lineId,
        UUID tagId,
        @Size(max = 128) String tagCode,
        @NotNull JsonNode targetValue,
        @Size(max = 255) String requestedBy,
        @NotBlank String reason) {}
