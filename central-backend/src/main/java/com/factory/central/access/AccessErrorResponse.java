package com.factory.central.access;

import java.util.List;

public record AccessErrorResponse(String code, String message, List<FieldErrorDetail> fieldErrors) {

    public record FieldErrorDetail(String field, String message) {}

    public static AccessErrorResponse of(String code, String message) {
        return new AccessErrorResponse(code, message, List.of());
    }

    public static AccessErrorResponse of(String code, String message, List<FieldErrorDetail> fieldErrors) {
        return new AccessErrorResponse(code, message, fieldErrors == null ? List.of() : fieldErrors);
    }
}
