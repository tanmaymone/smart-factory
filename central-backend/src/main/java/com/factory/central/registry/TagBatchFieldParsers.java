package com.factory.central.registry;

import java.util.List;

final class TagBatchFieldParsers {

    private TagBatchFieldParsers() {}

    static String required(TagBatchRowInput input, String field, List<TagBatchRowError> errors) {
        String value = input.get(field);
        if (value == null || value.isBlank()) {
            errors.add(new TagBatchRowError(field, RegistryErrorCode.VALIDATION_FAILED, "must not be blank"));
            return null;
        }
        return value.trim();
    }

    static Protocol parseProtocol(String raw, List<TagBatchRowError> errors) {
        if (raw == null || raw.isBlank()) {
            errors.add(new TagBatchRowError("protocol", RegistryErrorCode.VALIDATION_FAILED, "must not be blank"));
            return null;
        }
        try {
            return Protocol.valueOf(raw.trim());
        } catch (IllegalArgumentException ex) {
            errors.add(new TagBatchRowError(
                    "protocol", RegistryErrorCode.VALIDATION_FAILED, "must be a valid Protocol enum"));
            return null;
        }
    }

    static TagDataType parseDataType(String raw, List<TagBatchRowError> errors) {
        if (raw == null || raw.isBlank()) {
            errors.add(new TagBatchRowError("dataType", RegistryErrorCode.VALIDATION_FAILED, "must not be blank"));
            return null;
        }
        try {
            return TagDataType.valueOf(raw.trim());
        } catch (IllegalArgumentException ex) {
            errors.add(new TagBatchRowError(
                    "dataType", RegistryErrorCode.VALIDATION_FAILED, "must be a valid TagDataType enum"));
            return null;
        }
    }

    static RegistryLifecycle parseLifecycle(String raw, List<TagBatchRowError> errors) {
        try {
            return RegistryLifecycle.valueOf(raw.trim());
        } catch (IllegalArgumentException ex) {
            errors.add(new TagBatchRowError(
                    "lifecycle",
                    RegistryErrorCode.VALIDATION_FAILED,
                    "must be a valid RegistryLifecycle enum"));
            return null;
        }
    }

    static Boolean parseBoolean(String raw, String field, List<TagBatchRowError> errors) {
        if (raw == null || raw.isBlank()) {
            errors.add(new TagBatchRowError(field, RegistryErrorCode.VALIDATION_FAILED, "must not be blank"));
            return null;
        }
        String trimmed = raw.trim();
        if ("true".equalsIgnoreCase(trimmed) || "1".equals(trimmed)) {
            return true;
        }
        if ("false".equalsIgnoreCase(trimmed) || "0".equals(trimmed)) {
            return false;
        }
        errors.add(new TagBatchRowError(field, RegistryErrorCode.VALIDATION_FAILED, "must be true or false"));
        return null;
    }

    static Double parseDouble(String raw, String field, boolean required, List<TagBatchRowError> errors) {
        if (raw == null || raw.isBlank()) {
            if (required) {
                errors.add(new TagBatchRowError(field, RegistryErrorCode.VALIDATION_FAILED, "must not be blank"));
            }
            return null;
        }
        try {
            return Double.valueOf(raw.trim());
        } catch (NumberFormatException ex) {
            errors.add(new TagBatchRowError(field, RegistryErrorCode.VALIDATION_FAILED, "must be a number"));
            return null;
        }
    }

    static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
