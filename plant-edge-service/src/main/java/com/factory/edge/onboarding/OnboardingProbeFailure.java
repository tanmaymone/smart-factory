package com.factory.edge.onboarding;

/**
 * A failure before the session is up (DNS, pool saturation, discovery, TCP, connect, deadline) or
 * an interrupt anywhere. Both return HTTP 200 through the pre-session helper, never a 500.
 */
final class OnboardingProbeFailure extends RuntimeException {

    static final String TIMEOUT = "probe deadline exceeded";
    static final String POOL_SATURATED = "probe I/O pool saturated";
    static final String INTERRUPTED = "interrupted";

    private final boolean interrupted;

    private OnboardingProbeFailure(String message, boolean interrupted) {
        super(message, null, false, false);
        this.interrupted = interrupted;
    }

    static OnboardingProbeFailure of(String message) {
        return new OnboardingProbeFailure(message, false);
    }

    static OnboardingProbeFailure timedOut() {
        return new OnboardingProbeFailure(TIMEOUT, false);
    }

    static OnboardingProbeFailure poolSaturated() {
        return new OnboardingProbeFailure(POOL_SATURATED, false);
    }

    static OnboardingProbeFailure interrupted() {
        return new OnboardingProbeFailure(INTERRUPTED, true);
    }

    boolean isInterrupted() {
        return interrupted;
    }
}
