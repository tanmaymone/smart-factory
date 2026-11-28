package com.factory.central.onboarding;

import java.util.List;

public final class EquipmentTemplateDtos {

    private EquipmentTemplateDtos() {}

    public record EquipmentTemplateTagRow(
            String siteId,
            String lineId,
            String tagCode,
            String description,
            String protocol,
            String endpointUrl,
            String externalAddress,
            String dataType,
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
            String lifecycle) {}

    public record EquipmentTemplateDownloadUrls(String json, String csv) {}

    public record EquipmentTemplateSummary(
            String templateKey,
            String displayName,
            String equipmentType,
            String description,
            String defaultLineId,
            boolean prosysReady,
            int tagCount,
            List<String> supportedFormats,
            EquipmentTemplateDownloadUrls downloadUrls) {}

    public record EquipmentTemplateCatalogResponse(int schemaVersion, List<EquipmentTemplateSummary> templates) {}

    public record EquipmentTemplateJsonPayload(List<EquipmentTemplateTagRow> tags) {}
}