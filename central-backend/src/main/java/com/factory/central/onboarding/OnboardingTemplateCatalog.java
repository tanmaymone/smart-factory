package com.factory.central.onboarding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class OnboardingTemplateCatalog {

    private final int schemaVersion;
    private final Map<String, TemplateDefinition> byKey;

    public OnboardingTemplateCatalog(ObjectMapper objectMapper) {
        try (InputStream in =
                OnboardingTemplateCatalog.class.getResourceAsStream("/onboarding/equipment-templates.json")) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource onboarding/equipment-templates.json");
            }
            JsonNode root = objectMapper.readTree(in);
            this.schemaVersion = root.path("schemaVersion").asInt(1);
            Map<String, TemplateDefinition> map = new LinkedHashMap<>();
            for (JsonNode node : root.withArray("templates")) {
                TemplateDefinition def = TemplateDefinition.parse(node);
                map.put(def.templateKey(), def);
            }
            this.byKey = Map.copyOf(map);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load equipment-templates.json", ex);
        }
    }

    public EquipmentTemplateDtos.EquipmentTemplateCatalogResponse catalog() {
        List<EquipmentTemplateDtos.EquipmentTemplateSummary> summaries = new ArrayList<>();
        for (TemplateDefinition def : byKey.values()) {
            summaries.add(def.summary());
        }
        return new EquipmentTemplateDtos.EquipmentTemplateCatalogResponse(schemaVersion, List.copyOf(summaries));
    }

    public Optional<TemplateDefinition> find(String templateKey) {
        if (templateKey == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byKey.get(templateKey.trim()));
    }

    public EquipmentTemplateDtos.EquipmentTemplateJsonPayload jsonPayload(
            TemplateDefinition def, String siteId, String lineId, String endpointUrl) {
        return new EquipmentTemplateDtos.EquipmentTemplateJsonPayload(
                def.materializeRows(siteId, lineId, endpointUrl));
    }

    public String csvPayload(TemplateDefinition def, String siteId, String lineId, String endpointUrl) {
        return OnboardingTemplateCsvRenderer.render(def.materializeRows(siteId, lineId, endpointUrl));
    }

    public TemplateDefinition require(String templateKey) {
        return find(templateKey)
                .orElseThrow(() -> new OnboardingCatalogException(
                        OnboardingErrorCode.TEMPLATE_NOT_FOUND,
                        "Unknown equipment template: " + templateKey,
                        HttpStatus.NOT_FOUND,
                        List.of()));
    }

    record TemplateDefinition(
            String templateKey,
            String displayName,
            String equipmentType,
            String description,
            String defaultLineId,
            boolean prosysReady,
            String defaultSiteId,
            String defaultEndpointUrl,
            List<TagTemplate> tags) {

        EquipmentTemplateDtos.EquipmentTemplateSummary summary() {
            EquipmentTemplateDtos.EquipmentTemplateDownloadUrls urls =
                    new EquipmentTemplateDtos.EquipmentTemplateDownloadUrls(
                            "/api/onboarding/templates/" + templateKey + "?format=json",
                            "/api/onboarding/templates/" + templateKey + "?format=csv");
            return new EquipmentTemplateDtos.EquipmentTemplateSummary(
                    templateKey,
                    displayName,
                    equipmentType,
                    description,
                    defaultLineId,
                    prosysReady,
                    tags.size(),
                    List.of("json", "csv"),
                    urls);
        }

        List<EquipmentTemplateDtos.EquipmentTemplateTagRow> materializeRows(
                String siteId, String lineId, String endpointUrl) {
            String resolvedSite = siteId == null || siteId.isBlank() ? defaultSiteId : siteId.trim();
            String resolvedLine = lineId == null || lineId.isBlank() ? defaultLineId : lineId.trim();
            String resolvedEndpoint =
                    endpointUrl == null || endpointUrl.isBlank() ? defaultEndpointUrl : endpointUrl.trim();
            return tags.stream()
                    .map(t -> t.toRow(resolvedSite, resolvedLine, resolvedEndpoint))
                    .toList();
        }

        static TemplateDefinition parse(JsonNode node) {
            List<TagTemplate> tagList = new ArrayList<>();
            for (JsonNode tag : node.withArray("tags")) {
                tagList.add(TagTemplate.parse(tag));
            }
            return new TemplateDefinition(
                    node.path("templateKey").asText(),
                    node.path("displayName").asText(),
                    node.path("equipmentType").asText(),
                    node.path("description").asText(),
                    node.path("defaultLineId").asText(),
                    node.path("prosysReady").asBoolean(false),
                    node.path("defaultSiteId").asText("SITE-01"),
                    node.path("defaultEndpointUrl")
                            .asText("opc.tcp://localhost:53530/OPCUA/SimulationServer"),
                    List.copyOf(tagList));
        }
    }

    record TagTemplate(
            String tagCode,
            String description,
            String protocol,
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
            String lifecycle) {

        EquipmentTemplateDtos.EquipmentTemplateTagRow toRow(String siteId, String lineId, String endpointUrl) {
            return new EquipmentTemplateDtos.EquipmentTemplateTagRow(
                    siteId,
                    lineId,
                    tagCode,
                    description,
                    protocol,
                    endpointUrl,
                    externalAddress,
                    dataType,
                    unit,
                    writable,
                    minValue,
                    maxValue,
                    deadband,
                    warningLow,
                    warningHigh,
                    criticalLow,
                    criticalHigh,
                    interlockRuleKey,
                    lifecycle);
        }

        static TagTemplate parse(JsonNode tag) {
            return new TagTemplate(
                    tag.path("tagCode").asText(),
                    tag.path("description").asText(),
                    tag.path("protocol").asText("OPC_UA"),
                    tag.path("externalAddress").asText(),
                    tag.path("dataType").asText(),
                    textOrNull(tag, "unit"),
                    tag.path("writable").asBoolean(false),
                    doubleOrNull(tag, "minValue"),
                    doubleOrNull(tag, "maxValue"),
                    tag.path("deadband").asDouble(0),
                    doubleOrNull(tag, "warningLow"),
                    doubleOrNull(tag, "warningHigh"),
                    doubleOrNull(tag, "criticalLow"),
                    doubleOrNull(tag, "criticalHigh"),
                    textOrNull(tag, "interlockRuleKey"),
                    tag.path("lifecycle").asText("DRAFT"));
        }

        private static String textOrNull(JsonNode tag, String field) {
            JsonNode node = tag.get(field);
            if (node == null || node.isNull()) {
                return null;
            }
            return node.asText();
        }

        private static Double doubleOrNull(JsonNode tag, String field) {
            JsonNode node = tag.get(field);
            if (node == null || node.isNull()) {
                return null;
            }
            return node.asDouble();
        }
    }
}