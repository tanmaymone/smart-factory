package com.factory.central.registry;

import java.util.List;

public record RegistryErrorResponse(String code, String message, List<FieldErrorDetail> fieldErrors) {

    public record FieldErrorDetail(String field, String message) {}

    public static RegistryErrorResponse of(String code, String message) {
        return new RegistryErrorResponse(code, message, List.of());
    }

    public static RegistryErrorResponse of(String code, String message, List<FieldErrorDetail> fieldErrors) {
        return new RegistryErrorResponse(code, message, fieldErrors == null ? List.of() : fieldErrors);
    }
}
