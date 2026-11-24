package com.factory.central.registry;

import java.util.UUID;

public record TagMappingResponse(
        UUID id,
        String siteId,
        String lineId,
        String tagCode,
        String description,
        Protocol protocol,
        String endpointUrl,
        String externalAddress,
        TagDataType dataType,
        String unit,
        boolean writable,
        Double minValue,
        Double maxValue,
        double deadband,
        Double warningLow,
        Double warningHigh,
        Double criticalLow,
        Double criticalHigh,
        String interlockRuleKey,
        RegistryLifecycle lifecycle) {

    public static TagMappingResponse from(TagMapping tag) {
        return new TagMappingResponse(
                tag.getId(),
                tag.getSiteId(),
                tag.getLineId(),
                tag.getTagCode(),
                tag.getDescription(),
                tag.getProtocol(),
                tag.getEndpointUrl(),
                tag.getExternalAddress(),
                tag.getDataType(),
                tag.getUnit(),
                tag.isWritable(),
                tag.getMinValue(),
                tag.getMaxValue(),
                tag.getDeadband(),
                tag.getWarningLow(),
                tag.getWarningHigh(),
                tag.getCriticalLow(),
                tag.getCriticalHigh(),
                tag.getInterlockRuleKey(),
                tag.getLifecycle());
    }
}
