package com.factory.edge.onboarding;

import com.factory.edge.onboarding.OnboardingProbeResponse.TagDiagnostic;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;

/**
 * One submitted {@code nodeIds} index while the probe is still filling it in. A row whose session
 * came up stays {@code REACHABLE} even when its text never parsed: a parse failure is not
 * {@code UNREACHABLE}.
 */
final class OnboardingProbeRow {

    final String nodeId;
    NodeId resolved;
    String reachability = OnboardingProbeResponse.REACHABLE;
    Object value;
    String dataType;
    String quality = OnboardingProbeResponse.QUALITY_GOOD;
    String statusName;
    String error;

    OnboardingProbeRow(String nodeId) {
        this.nodeId = nodeId;
    }

    void bad(String error) {
        this.quality = OnboardingProbeResponse.QUALITY_BAD;
        this.value = null;
        this.statusName = null;
        this.error = error;
    }

    void unreachable(String error) {
        this.reachability = OnboardingProbeResponse.UNREACHABLE;
        this.quality = OnboardingProbeResponse.QUALITY_UNREACHABLE;
        this.value = null;
        this.dataType = null;
        this.statusName = null;
        this.error = error;
    }

    /** Exact duplicate text is read once and the diagnostic is copied onto each duplicate index. */
    void copyFrom(OnboardingProbeRow source) {
        this.reachability = source.reachability;
        this.value = source.value;
        this.dataType = source.dataType;
        this.quality = source.quality;
        this.statusName = source.statusName;
        this.error = source.error;
    }

    TagDiagnostic toDiagnostic() {
        return new TagDiagnostic(nodeId, reachability, value, dataType, quality, statusName, error);
    }
}
