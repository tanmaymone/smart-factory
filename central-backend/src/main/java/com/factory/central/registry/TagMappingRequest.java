package com.factory.central.registry;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TagMappingRequest(
        @NotBlank @Size(max = 64) String siteId,
        @NotBlank @Size(max = 64) String lineId,
        @NotBlank @Size(max = 128) String tagCode,
        @NotBlank @Size(max = 255) String description,
        @NotNull Protocol protocol,
        @NotBlank @Size(max = 512) String endpointUrl,
        @NotBlank @Size(max = 255) String externalAddress,
        @NotNull TagDataType dataType,
        @Size(max = 32) String unit,
        @NotNull Boolean writable,
        Double minValue,
        Double maxValue,
        @NotNull @DecimalMin(value = "0.0", inclusive = true) Double deadband,
        Double warningLow,
        Double warningHigh,
        Double criticalLow,
        Double criticalHigh,
        @Size(max = 128) String interlockRuleKey,
        RegistryLifecycle lifecycle) {}
