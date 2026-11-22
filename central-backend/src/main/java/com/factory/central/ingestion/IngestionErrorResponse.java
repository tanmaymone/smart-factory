package com.factory.central.ingestion;

import java.util.List;

public record IngestionErrorResponse(
        String code, String message, List<IngestReadingError.FieldErrorDetail> fieldErrors) {

    public static IngestionErrorResponse of(String code, String message) {
        return new IngestionErrorResponse(code, message, List.of());
    }

    public static IngestionErrorResponse of(
            String code, String message, List<IngestReadingError.FieldErrorDetail> fieldErrors) {
        return new IngestionErrorResponse(code, message, fieldErrors == null ? List.of() : fieldErrors);
    }
}
