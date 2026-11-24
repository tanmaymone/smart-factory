package com.factory.central.access;

import java.util.List;
import org.springframework.http.HttpStatus;

public class AccessException extends RuntimeException {

    private final String code;
    private final HttpStatus status;
    private final List<AccessErrorResponse.FieldErrorDetail> fieldErrors;

    public AccessException(String code, String message, HttpStatus status) {
        this(code, message, status, List.of());
    }

    public AccessException(
            String code,
            String message,
            HttpStatus status,
            List<AccessErrorResponse.FieldErrorDetail> fieldErrors) {
        super(message);
        this.code = code;
        this.status = status;
        this.fieldErrors = fieldErrors == null ? List.of() : fieldErrors;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public List<AccessErrorResponse.FieldErrorDetail> getFieldErrors() {
        return fieldErrors;
    }
}
