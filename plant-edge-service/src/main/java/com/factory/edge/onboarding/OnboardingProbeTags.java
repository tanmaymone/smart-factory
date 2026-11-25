package com.factory.edge.onboarding;

import com.factory.edge.onboarding.OnboardingProbeResponse.TagDiagnostic;
import java.util.ArrayList;
import java.util.List;

/**
 * The one pre-session builder. TCP failure, DNS failure, a saturated probe I/O pool, PKI, a
 * deadline gone before the session came up, discovery or connect failure, a rejected handshake, and
 * an interrupt all return through here, so none of them is built inline on an early exit.
 */
final class OnboardingProbeTags {

    private OnboardingProbeTags() {}

    /**
     * Keeps the submitted {@code nodeIds} order and drops nothing: a null, a blank, and each
     * duplicate become their own tag. Omitted, null, or empty {@code nodeIds} yields {@code []}.
     */
    static OnboardingProbeResponse unreachable(OnboardingProbeRequest request, String error) {
        List<String> nodeIds = request.nodeIds() == null ? List.of() : request.nodeIds();
        List<TagDiagnostic> tags = new ArrayList<>(nodeIds.size());
        for (String nodeId : nodeIds) {
            tags.add(
                    new TagDiagnostic(
                            nodeId,
                            OnboardingProbeResponse.UNREACHABLE,
                            null,
                            null,
                            OnboardingProbeResponse.QUALITY_UNREACHABLE,
                            null,
                            null));
        }
        return new OnboardingProbeResponse(
                request.endpointUrl(), request.securityMode(), false, error, List.copyOf(tags));
    }
}
