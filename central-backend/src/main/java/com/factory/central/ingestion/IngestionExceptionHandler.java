package com.factory.central.ingestion;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = TelemetryController.class)
public class IngestionExceptionHandler {

    @ExceptionHandler(IngestionException.class)
    public ResponseEntity<IngestionErrorResponse> handleIngestion(IngestionException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(IngestionErrorResponse.of(ex.getCode(), ex.getMessage(), ex.getFieldErrors()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<IngestionErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(IngestionErrorResponse.of(
                        IngestionErrorCode.VALIDATION_FAILED, "Malformed or unreadable request body"));
    }
}
