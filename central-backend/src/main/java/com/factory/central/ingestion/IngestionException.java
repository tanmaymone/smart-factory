package com.factory.central.ingestion;

import java.util.List;
import org.springframework.http.HttpStatus;

public class IngestionException extends RuntimeException {

    private final String code;
    private final HttpStatus status;
    private final List<IngestReadingError.FieldErrorDetail> fieldErrors;

    public IngestionException(String code, String message, HttpStatus status) {
        this(code, message, status, List.of());
    }

    public IngestionException(
            String code, String message, HttpStatus status, List<IngestReadingError.FieldErrorDetail> fieldErrors) {
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

    public List<IngestReadingError.FieldErrorDetail> getFieldErrors() {
        return fieldErrors;
    }
}
