package com.factory.edge.onboarding;

import java.util.List;

/**
 * Completed probe envelope (HTTP 200). Field names are fixed for the M3.5 wizard. {@code reachable}
 * is true once the short-lived session came up, even when the later {@code Server_NamespaceArray}
 * read set {@code error}.
 */
public record OnboardingProbeResponse(
        String endpointUrl,
        String securityMode,
        boolean reachable,
        String error,
        List<TagDiagnostic> tags) {

    public static final String REACHABLE = "REACHABLE";
    public static final String UNREACHABLE = "UNREACHABLE";
    public static final String QUALITY_GOOD = "GOOD";
    public static final String QUALITY_BAD = "BAD";
    public static final String QUALITY_UNREACHABLE = "UNREACHABLE";

    /**
     * One row per submitted {@code nodeIds} entry, in submitted order. {@code quality} is only
     * {@code GOOD}, {@code BAD}, or {@code UNREACHABLE}; an uncertain OPC status is {@code BAD} with
     * its {@code Uncertain_*} name kept on {@code statusName}.
     */
    public record TagDiagnostic(
            String nodeId,
            String reachability,
            Object value,
            String dataType,
            String quality,
            String statusName,
            String error) {}
}
