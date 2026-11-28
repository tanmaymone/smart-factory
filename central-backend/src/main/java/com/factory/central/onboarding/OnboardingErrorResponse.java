package com.factory.central.onboarding;

import java.util.List;

public record OnboardingErrorResponse(String code, String message, List<FieldErrorDetail> fieldErrors) {

    public record FieldErrorDetail(String field, String message) {}

    public static OnboardingErrorResponse of(String code, String message) {
        return new OnboardingErrorResponse(code, message, List.of());
    }

    public static OnboardingErrorResponse of(String code, String message, List<FieldErrorDetail> fieldErrors) {
        return new OnboardingErrorResponse(
                code, message, fieldErrors == null ? List.of() : List.copyOf(fieldErrors));
    }
}