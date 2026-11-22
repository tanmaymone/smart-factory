package com.factory.central.registry;

import java.util.List;
import org.springframework.http.HttpStatus;

public class RegistryException extends RuntimeException {

    private final String code;
    private final HttpStatus status;
    private final List<RegistryErrorResponse.FieldErrorDetail> fieldErrors;

    public RegistryException(String code, String message, HttpStatus status) {
        this(code, message, status, List.of());
    }

    public RegistryException(
            String code,
            String message,
            HttpStatus status,
            List<RegistryErrorResponse.FieldErrorDetail> fieldErrors) {
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

    public List<RegistryErrorResponse.FieldErrorDetail> getFieldErrors() {
        return fieldErrors;
    }
}
