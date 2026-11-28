package com.factory.central.onboarding;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/onboarding")
public class OnboardingTemplateController {

    private final OnboardingTemplateCatalog catalog;

    public OnboardingTemplateController(OnboardingTemplateCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping(value = "/templates", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EquipmentTemplateDtos.EquipmentTemplateCatalogResponse> listTemplates() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(catalog.catalog());
    }

    @GetMapping(value = "/templates/{templateKey}")
    public ResponseEntity<?> download(
            @PathVariable String templateKey,
            @RequestParam(defaultValue = "json") String format,
            @RequestParam(required = false) String siteId,
            @RequestParam(required = false) String lineId,
            @RequestParam(required = false) String endpointUrl,
            @RequestParam(defaultValue = "true") boolean download) {
        if (siteId != null && !siteId.isBlank()) {
            OnboardingNaturalKey.requireSiteId(siteId);
        }
        if (lineId != null && !lineId.isBlank()) {
            OnboardingNaturalKey.requireLineId(lineId);
        }
        if (endpointUrl != null && !endpointUrl.isBlank()) {
            OnboardingNaturalKey.requireEndpointUrl(endpointUrl);
        }
        String normalizedFormat = format == null ? "json" : format.trim().toLowerCase();
        if (!"json".equals(normalizedFormat) && !"csv".equals(normalizedFormat)) {
            throw new OnboardingCatalogException(
                    OnboardingErrorCode.UNSUPPORTED_TEMPLATE_FORMAT,
                    "Unsupported template format",
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    OnboardingNaturalKey.invalidFormat());
        }
        OnboardingTemplateCatalog.TemplateDefinition def = catalog.require(templateKey);
        String filename = "smart-factory-" + def.templateKey() + "-tags." + normalizedFormat;
        if ("csv".equals(normalizedFormat)) {
            String body = catalog.csvPayload(def, siteId, lineId, endpointUrl);
            ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .contentType(MediaType.parseMediaType("text/csv; charset=utf-8"));
            if (download) {
                builder.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
            }
            return builder.body(body);
        }
        EquipmentTemplateDtos.EquipmentTemplateJsonPayload payload =
                catalog.jsonPayload(def, siteId, lineId, endpointUrl);
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_JSON);
        if (download) {
            builder.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
        }
        return builder.body(payload);
    }
}