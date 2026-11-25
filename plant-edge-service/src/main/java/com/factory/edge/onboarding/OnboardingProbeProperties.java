package com.factory.edge.onboarding;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The probe is a plant-LAN port oracle, so it stays off unless an operator opts in. Dev and the
 * Prosys protocol tests set it true.
 */
@ConfigurationProperties(prefix = "app.onboarding")
public class OnboardingProbeProperties {

    private boolean probeEnabled = false;

    public boolean isProbeEnabled() {
        return probeEnabled;
    }

    public void setProbeEnabled(boolean probeEnabled) {
        this.probeEnabled = probeEnabled;
    }
}
