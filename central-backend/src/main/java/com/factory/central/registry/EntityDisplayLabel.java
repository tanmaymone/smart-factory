package com.factory.central.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class EntityDisplayLabel {

    static final String DEFAULT_SITE = "Site";
    static final String DEFAULT_LINE = "Line";
    private static final Pattern LABEL_PATTERN = Pattern.compile("^[\\p{L}]+(?:[\\s-][\\p{L}]+)*$");
    private static final int MIN_LEN = 2;
    private static final int MAX_LEN = 24;

    private EntityDisplayLabel() {}

    static String normalizeSite(String raw) {
        return normalize(raw, DEFAULT_SITE);
    }

    static String normalizeLine(String raw) {
        return normalize(raw, DEFAULT_LINE);
    }

    static void assertValid(String value, String field) {
        if (value == null || value.length() < MIN_LEN || value.length() > MAX_LEN || !LABEL_PATTERN.matcher(value).matches()) {
            throw new RegistryException(
                    RegistryErrorCode.VALIDATION_FAILED,
                    "Request validation failed",
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    List.of(new RegistryErrorResponse.FieldErrorDetail(
                            field,
                            "must be 2-24 letters with single spaces or hyphens between words")));
        }
    }

    static void validateOptional(String raw, String field, List<RegistryErrorResponse.FieldErrorDetail> errors) {
        if (raw == null) {
            return;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            errors.add(new RegistryErrorResponse.FieldErrorDetail(
                    field,
                    "must be 2-24 letters with single spaces or hyphens between words"));
            return;
        }
        if (trimmed.length() < MIN_LEN
                || trimmed.length() > MAX_LEN
                || !LABEL_PATTERN.matcher(trimmed).matches()) {
            errors.add(new RegistryErrorResponse.FieldErrorDetail(
                    field,
                    "must be 2-24 letters with single spaces or hyphens between words"));
        }
    }

    static List<RegistryErrorResponse.FieldErrorDetail> validateCreate(String siteLabel, String lineLabel) {
        List<RegistryErrorResponse.FieldErrorDetail> errors = new ArrayList<>();
        validateOptional(siteLabel, "siteLabel", errors);
        validateOptional(lineLabel, "lineLabel", errors);
        return errors;
    }

    private static String normalize(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return raw.trim();
    }
}