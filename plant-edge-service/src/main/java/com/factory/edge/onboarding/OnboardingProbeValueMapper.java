package com.factory.edge.onboarding;

import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UNumber;

/**
 * Maps a Milo scalar onto a JSON-safe value before it leaves the service. Jackson must never see a
 * raw Milo object, a raw array, or a bare {@code NaN} token.
 */
final class OnboardingProbeValueMapper {

    private OnboardingProbeValueMapper() {}

    static Object toJsonValue(Object raw) {
        if (raw == null || raw instanceof Boolean || raw instanceof String) {
            return raw;
        }
        if (raw instanceof UNumber unsigned) {
            // ULong.MAX must serialize as the JSON number 18446744073709551615.
            return unsigned.toBigInteger();
        }
        if (raw instanceof Float value) {
            return value.isNaN() || value.isInfinite() ? null : value;
        }
        if (raw instanceof Double value) {
            return value.isNaN() || value.isInfinite() ? null : value;
        }
        if (raw instanceof Byte || raw instanceof Short || raw instanceof Integer
                || raw instanceof Long) {
            return raw;
        }
        if (raw instanceof DateTime dateTime) {
            return dateTime.isNull() ? null : dateTime.getJavaInstant().toString();
        }
        // ByteString, LocalizedText, NodeId, QualifiedName, GUID, arrays, and Matrix.
        return String.valueOf(raw);
    }
}
