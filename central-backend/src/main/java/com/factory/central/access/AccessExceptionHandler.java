package com.factory.central.access;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = ActorAdminController.class)
public class AccessExceptionHandler {

    @ExceptionHandler(AccessException.class)
    public ResponseEntity<AccessErrorResponse> handleAccess(AccessException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(AccessErrorResponse.of(ex.getCode(), ex.getMessage(), ex.getFieldErrors()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<AccessErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<AccessErrorResponse.FieldErrorDetail> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> new AccessErrorResponse.FieldErrorDetail(err.getField(), err.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest()
                .body(AccessErrorResponse.of(
                        AccessErrorCode.VALIDATION_FAILED, "Request validation failed", fields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<AccessErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(AccessErrorResponse.of(AccessErrorCode.VALIDATION_FAILED, "Malformed or unreadable request body"));
    }
}
