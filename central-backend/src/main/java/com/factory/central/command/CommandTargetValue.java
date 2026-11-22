package com.factory.central.command;

import com.fasterxml.jackson.databind.JsonNode;
import com.factory.central.registry.TagDataType;
import com.factory.central.registry.TagMapping;
import java.util.List;

final class CommandTargetValue {

    private CommandTargetValue() {}

    record Parsed(Object value, String stored) {}

    static Parsed parse(TagMapping tag, JsonNode targetValue) {
        if (targetValue == null || targetValue.isNull()) {
            throw invalid("targetValue is required", "targetValue", "must not be null");
        }
        return switch (tag.getDataType()) {
            case FLOAT -> parseFloat(targetValue);
            case INT -> parseInt(targetValue);
            case BOOLEAN -> parseBoolean(targetValue);
            case STRING -> parseString(targetValue);
        };
    }

    static void assertWithinBounds(TagMapping tag, Object value) {
        Double min = tag.getMinValue();
        Double max = tag.getMaxValue();
        if (min != null && max != null && min > max) {
            throw new CommandException(
                    CommandErrorCode.TAG_BOUNDS_MISCONFIGURED,
                    "Tag minValue must be less than or equal to maxValue",
                    org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                    null,
                    List.of(new CommandErrorResponse.FieldErrorDetail(
                            "minValue", "minValue must be less than or equal to maxValue")));
        }
        if (!(value instanceof Number number)) {
            return;
        }
        double numeric = number.doubleValue();
        if (!Double.isFinite(numeric)) {
            throw invalid("targetValue must be a finite number", "targetValue", "must be finite");
        }
        if (min != null && numeric < min) {
            throw outOfRange("targetValue is below minValue (" + min + ")");
        }
        if (max != null && numeric > max) {
            throw outOfRange("targetValue is above maxValue (" + max + ")");
        }
    }

    private static Parsed parseFloat(JsonNode node) {
        if (!node.isNumber()) {
            throw invalid("targetValue must be numeric for FLOAT tags", "targetValue", "must be numeric");
        }
        double value = node.asDouble();
        if (!Double.isFinite(value)) {
            throw invalid("targetValue must be a finite number", "targetValue", "must be finite");
        }
        return new Parsed(value, Double.toString(value));
    }

    private static Parsed parseInt(JsonNode node) {
        if (!node.isNumber()) {
            throw invalid("targetValue must be numeric for INT tags", "targetValue", "must be numeric");
        }
        double asDouble = node.asDouble();
        if (!Double.isFinite(asDouble)) {
            throw invalid("targetValue must be a finite number", "targetValue", "must be finite");
        }
        if (!node.isIntegralNumber() && Math.rint(asDouble) != asDouble) {
            throw invalid(
                    "targetValue must be an integer for INT tags", "targetValue", "must be an integer");
        }
        long value = node.asLong();
        return new Parsed(value, Long.toString(value));
    }

    private static Parsed parseBoolean(JsonNode node) {
        if (node.isBoolean()) {
            boolean value = node.asBoolean();
            return new Parsed(value, Boolean.toString(value));
        }
        if (node.isTextual()) {
            String text = node.asText();
            if ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text)) {
                boolean value = Boolean.parseBoolean(text);
                return new Parsed(value, Boolean.toString(value));
            }
        }
        throw invalid("targetValue must be a boolean for BOOLEAN tags", "targetValue", "must be a boolean");
    }

    private static Parsed parseString(JsonNode node) {
        if (!node.isTextual()) {
            throw invalid("targetValue must be a string for STRING tags", "targetValue", "must be a string");
        }
        String value = node.asText();
        return new Parsed(value, value);
    }

    private static CommandException invalid(String message, String field, String fieldMessage) {
        return new CommandException(
                CommandErrorCode.INVALID_VALUE,
                message,
                org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                null,
                List.of(new CommandErrorResponse.FieldErrorDetail(field, fieldMessage)));
    }

    private static CommandException outOfRange(String message) {
        return new CommandException(
                CommandErrorCode.TARGET_OUT_OF_RANGE,
                message,
                org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                null,
                List.of(new CommandErrorResponse.FieldErrorDetail("targetValue", message)));
    }
}
