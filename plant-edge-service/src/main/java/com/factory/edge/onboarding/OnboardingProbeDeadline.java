package com.factory.edge.onboarding;

/**
 * One shared budget, started only after {@code tryLock()} succeeds, and charged for DNS, the
 * pre-discovery TCP connect, discovery, session connect, and every read. A step that sees
 * {@code remainingMillis() <= 0} is not started.
 */
final class OnboardingProbeDeadline {

    private final long deadlineNanos;

    private OnboardingProbeDeadline(long deadlineNanos) {
        this.deadlineNanos = deadlineNanos;
    }

    static OnboardingProbeDeadline start(long budgetMs) {
        return new OnboardingProbeDeadline(System.nanoTime() + budgetMs * 1_000_000L);
    }

    long remainingMillis() {
        long remaining = (deadlineNanos - System.nanoTime()) / 1_000_000L;
        return Math.max(remaining, 0L);
    }

    boolean expired() {
        return remainingMillis() <= 0L;
    }
}
