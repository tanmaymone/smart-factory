package com.factory.central.onboarding;

import java.util.List;
import org.springframework.http.HttpStatus;

public class OnboardingCatalogException extends RuntimeException {

    private final String code;
    private final HttpStatus status;
    private final List<OnboardingErrorResponse.FieldErrorDetail> fieldErrors;

    public OnboardingCatalogException(
            String code, String message, HttpStatus status, List<OnboardingErrorResponse.FieldErrorDetail> fieldErrors) {
        super(message);
        this.code = code;
        this.status = status;
        this.fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public List<OnboardingErrorResponse.FieldErrorDetail> getFieldErrors() {
        return fieldErrors;
    }
}