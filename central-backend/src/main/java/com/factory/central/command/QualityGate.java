package com.factory.central.command;

import java.util.Locale;

/**
 * Trust gate for interlocks and other live-tag decisions. Null, blank, and non-{@code GOOD} stay
 * non-good. Matches the edge string contract without a Milo dependency.
 */
public final class QualityGate {

    private QualityGate() {}

    /** Trim, uppercase with {@link Locale#ROOT}, accept only {@code GOOD}. */
    public static boolean requireGood(String quality) {
        if (quality == null) {
            return false;
        }
        String normalized = quality.trim().toUpperCase(Locale.ROOT);
        return "GOOD".equals(normalized);
    }
}
