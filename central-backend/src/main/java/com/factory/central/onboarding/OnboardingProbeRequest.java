package com.factory.central.onboarding;

import java.util.List;

/**
 * securityMode is a String, not an enum: enum binding would answer an unknown token with 400 before
 * the validator can return 422 SECURITY_MODE_UNSUPPORTED.
 */
public record OnboardingProbeRequest(String securityMode, String endpointUrl, List<String> nodeIds) {}
