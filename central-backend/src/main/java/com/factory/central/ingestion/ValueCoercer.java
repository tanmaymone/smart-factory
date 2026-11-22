package com.factory.central.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.factory.central.registry.TagDataType;
import com.factory.central.registry.TagMapping;
import java.util.List;

final class ValueCoercer {

    private ValueCoercer() {}

    static CoercedValue coerce(TagMapping tag, JsonNode valueNode) {
        if (valueNode == null || valueNode.isNull()) {
            throw new TelemetryPayloadParser.ReadingRejectException(
                    IngestionErrorCode.INVALID_VALUE,
                    "value is required",
                    List.of(new IngestReadingError.FieldErrorDetail("value", "must not be null")));
        }
        return switch (tag.getDataType()) {
            case FLOAT -> coerceFloat(valueNode);
            case INT -> coerceInt(valueNode);
            case BOOLEAN -> coerceBoolean(valueNode);
            case STRING -> coerceString(valueNode);
        };
    }

    private static CoercedValue coerceFloat(JsonNode valueNode) {
        if (!valueNode.isNumber()) {
            throw invalidNumber();
        }
        double value = valueNode.asDouble();
        return new CoercedValue(value, value, null, null);
    }

    private static CoercedValue coerceInt(JsonNode valueNode) {
        if (!valueNode.isIntegralNumber() && !(valueNode.isFloatingPointNumber() && isWhole(valueNode.asDouble()))) {
            if (!valueNode.isNumber()) {
                throw invalidNumber();
            }
            throw new TelemetryPayloadParser.ReadingRejectException(
                    IngestionErrorCode.INVALID_VALUE,
                    "value must be an integer for INT tags",
                    List.of(new IngestReadingError.FieldErrorDetail("value", "must be an integer")));
        }
        long value = valueNode.asLong();
        return new CoercedValue(value, (double) value, null, null);
    }

    private static CoercedValue coerceBoolean(JsonNode valueNode) {
        if (valueNode.isBoolean()) {
            boolean value = valueNode.asBoolean();
            return new CoercedValue(value, null, value, null);
        }
        if (valueNode.isTextual()) {
            String text = valueNode.asText();
            if ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text)) {
                boolean value = Boolean.parseBoolean(text);
                return new CoercedValue(value, null, value, null);
            }
        }
        throw new TelemetryPayloadParser.ReadingRejectException(
                IngestionErrorCode.INVALID_VALUE,
                "value must be a boolean for BOOLEAN tags",
                List.of(new IngestReadingError.FieldErrorDetail("value", "must be a boolean")));
    }

    private static CoercedValue coerceString(JsonNode valueNode) {
        if (valueNode.isTextual()) {
            String value = valueNode.asText();
            return new CoercedValue(value, null, null, value);
        }
        throw new TelemetryPayloadParser.ReadingRejectException(
                IngestionErrorCode.INVALID_VALUE,
                "value must be a string for STRING tags",
                List.of(new IngestReadingError.FieldErrorDetail("value", "must be a string")));
    }

    private static boolean isWhole(double value) {
        return Double.isFinite(value) && Math.rint(value) == value;
    }

    private static TelemetryPayloadParser.ReadingRejectException invalidNumber() {
        return new TelemetryPayloadParser.ReadingRejectException(
                IngestionErrorCode.INVALID_VALUE,
                "value must be numeric",
                List.of(new IngestReadingError.FieldErrorDetail("value", "must be numeric")));
    }

    record CoercedValue(Object value, Double numericValue, Boolean booleanValue, String stringValue) {}
}
