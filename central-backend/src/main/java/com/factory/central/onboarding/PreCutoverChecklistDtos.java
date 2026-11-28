package com.factory.central.onboarding;

import java.util.List;

public final class PreCutoverChecklistDtos {

    private PreCutoverChecklistDtos() {}

    public enum ChecklistCategory {
        OPC_UA_SECURITY,
        MQTT_BROKER_SECURITY,
        HEARTBEAT_AND_STALENESS,
        TIMESCALEDB_RETENTION,
        EDGE_PROVISIONING
    }

    public enum ChecklistVerificationMethod {
        MANUAL_COMMAND,
        UI_INSPECTION,
        EDGE_DIAGNOSTICS,
        DEFERRED_MILESTONE_GATE
    }

    public enum ChecklistSeverity {
        CUTOVER_BLOCKER,
        DEV_ADVISORY
    }

    public record PreCutoverChecklistItem(
            String id,
            ChecklistCategory category,
            String title,
            String description,
            ChecklistVerificationMethod verificationMethod,
            String verificationCommand,
            String passCriteria,
            ChecklistSeverity severity,
            boolean runnableInCurrentRelease,
            String milestoneGate,
            String currentDevPosture,
            String productionRequirement) {}

    public record PreCutoverChecklistSummary(
            int total, int runnableNow, int deferredMilestoneGates, int cutoverBlockers) {}

    public record PreCutoverChecklistResponse(
            int schemaVersion,
            String releaseScope,
            String playbookPath,
            PreCutoverChecklistSummary summary,
            List<PreCutoverChecklistItem> items) {}
}