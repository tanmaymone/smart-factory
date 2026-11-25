package com.factory.central.onboarding;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProbeErrorResponse(String code, String message, List<FieldErrorDetail> fieldErrors) {

    public record FieldErrorDetail(String field, String message) {}

    public static ProbeErrorResponse of(String code, String message) {
        return new ProbeErrorResponse(code, message, null);
    }
}
