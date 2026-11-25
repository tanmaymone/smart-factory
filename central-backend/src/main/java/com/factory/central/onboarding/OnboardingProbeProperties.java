package com.factory.central.onboarding;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.onboarding")
public class OnboardingProbeProperties {

    /** Off unless explicitly opted in: this route is a scanner on the plant LAN. */
    private boolean probeEnabled = false;

    /** One local edge beside Central for the dev/onboarding path. Not per-site routing. */
    private String edgeBaseUrl = "http://127.0.0.1:8081";

    public boolean isProbeEnabled() {
        return probeEnabled;
    }

    public void setProbeEnabled(boolean probeEnabled) {
        this.probeEnabled = probeEnabled;
    }

    public String getEdgeBaseUrl() {
        return edgeBaseUrl;
    }

    public void setEdgeBaseUrl(String edgeBaseUrl) {
        this.edgeBaseUrl = edgeBaseUrl == null || edgeBaseUrl.isBlank()
                ? "http://127.0.0.1:8081"
                : stripTrailingSlashes(edgeBaseUrl.trim());
    }

    private static String stripTrailingSlashes(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result.isEmpty() ? "http://127.0.0.1:8081" : result;
    }
}
