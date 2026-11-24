package com.factory.edge.opcua;

import java.util.Locale;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;

/**
 * Trust gate for last-good, interlocks, setpoints, and command ACK. Uncertain is not good — {@code
 * StatusCode#isBad()} is false for Uncertain.
 */
public final class QualityGate {

    private QualityGate() {}

    public static boolean requireGood(DataValue value) {
        if (value == null) {
            return false;
        }
        StatusCode status = value.getStatusCode();
        return status != null && status.isGood();
    }

    public static boolean requireGood(StatusCode status) {
        return status != null && status.isGood();
    }

    /** Trim, uppercase with {@link Locale#ROOT}, accept only {@code GOOD}. Null/blank stay non-good. */
    public static boolean requireGood(String quality) {
        if (quality == null) {
            return false;
        }
        String normalized = quality.trim().toUpperCase(Locale.ROOT);
        return "GOOD".equals(normalized);
    }
}
