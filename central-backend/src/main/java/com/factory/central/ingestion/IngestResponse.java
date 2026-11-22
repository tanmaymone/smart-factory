package com.factory.central.ingestion;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record IngestResponse(
        Integer accepted,
        List<IngestReadingError> errors,
        String code,
        String message,
        List<IngestReadingError.FieldErrorDetail> fieldErrors) {

    public static IngestResponse accepted(int accepted, List<IngestReadingError> errors) {
        return new IngestResponse(accepted, errors, null, null, null);
    }

    public static IngestResponse rejected(
            String code, String message, List<IngestReadingError> errors) {
        return new IngestResponse(0, errors, code, message, List.of());
    }

    public static IngestResponse bufferFull() {
        return new IngestResponse(
                0, List.of(), IngestionErrorCode.INGEST_BUFFER_FULL, "Telemetry history buffer is full", List.of());
    }
}
