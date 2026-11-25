package com.factory.edge.onboarding;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Probe error document. 422 carries {@code fieldErrors}; 400, 403, 409, and 413 are {@code code} +
 * {@code message} only, so {@code fieldErrors} must be omitted rather than serialized as null.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProbeErrorResponse(
        String code, String message, List<FieldErrorDetail> fieldErrors) {

    public record FieldErrorDetail(String field, String message) {}

    public static ProbeErrorResponse of(String code, String message) {
        return new ProbeErrorResponse(code, message, null);
    }
}
