package com.factory.central.onboarding;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(
        assignableTypes = {OnboardingTemplateController.class, OnboardingChecklistController.class})
public class OnboardingExceptionHandler {

    @ExceptionHandler(OnboardingCatalogException.class)
    public ResponseEntity<OnboardingErrorResponse> handleCatalog(OnboardingCatalogException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(OnboardingErrorResponse.of(ex.getCode(), ex.getMessage(), ex.getFieldErrors()));
    }
}