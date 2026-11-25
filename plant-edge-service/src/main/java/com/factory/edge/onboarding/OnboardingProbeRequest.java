package com.factory.edge.onboarding;

import java.util.List;

/**
 * Onboarding probe request. {@code securityMode} is a {@code String}, not an enum: Jackson enum
 * binding would reject {@code Sign} or {@code none} as 400 before the 422 validator runs.
 */
public record OnboardingProbeRequest(
        String endpointUrl, String securityMode, List<String> nodeIds) {}
