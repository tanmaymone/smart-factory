package com.factory.central.onboarding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class OnboardingChecklistCatalog {

    private final int schemaVersion;
    private final String releaseScope;
    private final String playbookPath;
    private final List<PreCutoverChecklistDtos.PreCutoverChecklistItem> items;

    public OnboardingChecklistCatalog(ObjectMapper objectMapper) {
        try (InputStream in =
                OnboardingChecklistCatalog.class.getResourceAsStream("/onboarding/pre-cutover-checklist.json")) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource onboarding/pre-cutover-checklist.json");
            }
            JsonNode root = objectMapper.readTree(in);
            this.schemaVersion = root.path("schemaVersion").asInt(1);
            this.releaseScope = root.path("releaseScope").asText("M3.6");
            this.playbookPath = root.path("playbookPath").asText("PLANT_ONBOARDING_PLAYBOOK.md");
            List<PreCutoverChecklistDtos.PreCutoverChecklistItem> parsed = new ArrayList<>();
            for (JsonNode node : root.withArray("items")) {
                parsed.add(parseItem(node));
            }
            this.items = List.copyOf(parsed);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load pre-cutover-checklist.json", ex);
        }
    }

    public PreCutoverChecklistDtos.PreCutoverChecklistResponse response(
            Optional<PreCutoverChecklistDtos.ChecklistCategory> category) {
        List<PreCutoverChecklistDtos.PreCutoverChecklistItem> filtered =
                category.map(c -> items.stream().filter(i -> i.category() == c).toList()).orElse(items);
        int runnable = 0;
        int deferred = 0;
        int blockers = 0;
        for (PreCutoverChecklistDtos.PreCutoverChecklistItem item : filtered) {
            if (item.runnableInCurrentRelease()) {
                runnable++;
            } else {
                deferred++;
            }
            if (item.severity() == PreCutoverChecklistDtos.ChecklistSeverity.CUTOVER_BLOCKER) {
                blockers++;
            }
        }
        PreCutoverChecklistDtos.PreCutoverChecklistSummary summary =
                new PreCutoverChecklistDtos.PreCutoverChecklistSummary(
                        filtered.size(), runnable, deferred, blockers);
        return new PreCutoverChecklistDtos.PreCutoverChecklistResponse(
                schemaVersion, releaseScope, playbookPath, summary, filtered);
    }

    public PreCutoverChecklistDtos.ChecklistCategory parseCategory(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new OnboardingCatalogException(
                    OnboardingErrorCode.INVALID_PARAMETER,
                    "Invalid value for parameter 'category'",
                    HttpStatus.BAD_REQUEST,
                    List.of(new OnboardingErrorResponse.FieldErrorDetail(
                            "category",
                            "must be one of OPC_UA_SECURITY, MQTT_BROKER_SECURITY, HEARTBEAT_AND_STALENESS, TIMESCALEDB_RETENTION, EDGE_PROVISIONING")));
        }
        try {
            return PreCutoverChecklistDtos.ChecklistCategory.valueOf(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw new OnboardingCatalogException(
                    OnboardingErrorCode.INVALID_PARAMETER,
                    "Invalid value for parameter 'category'",
                    HttpStatus.BAD_REQUEST,
                    List.of(new OnboardingErrorResponse.FieldErrorDetail(
                            "category",
                            "must be one of OPC_UA_SECURITY, MQTT_BROKER_SECURITY, HEARTBEAT_AND_STALENESS, TIMESCALEDB_RETENTION, EDGE_PROVISIONING")));
        }
    }

    private static PreCutoverChecklistDtos.PreCutoverChecklistItem parseItem(JsonNode node) {
        String verification = node.path("verificationCommand").asText(null);
        if (verification != null && verification.isBlank()) {
            verification = null;
        }
        String gate = node.path("milestoneGate").asText(null);
        if (gate != null && gate.isBlank()) {
            gate = null;
        }
        return new PreCutoverChecklistDtos.PreCutoverChecklistItem(
                node.path("id").asText(),
                PreCutoverChecklistDtos.ChecklistCategory.valueOf(node.path("category").asText()),
                node.path("title").asText(),
                node.path("description").asText(),
                PreCutoverChecklistDtos.ChecklistVerificationMethod.valueOf(
                        node.path("verificationMethod").asText()),
                verification,
                node.path("passCriteria").asText(),
                PreCutoverChecklistDtos.ChecklistSeverity.valueOf(node.path("severity").asText()),
                node.path("runnableInCurrentRelease").asBoolean(false),
                gate,
                node.path("currentDevPosture").asText(),
                node.path("productionRequirement").asText());
    }
}