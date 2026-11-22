package com.factory.central.ingestion;

import java.util.List;

public record IngestReadingError(
        int index,
        String tagCode,
        String externalAddress,
        String code,
        String message,
        List<FieldErrorDetail> fieldErrors) {

    public record FieldErrorDetail(String field, String message) {}

    public static IngestReadingError of(
            int index, String tagCode, String externalAddress, String code, String message) {
        return new IngestReadingError(index, tagCode, externalAddress, code, message, List.of());
    }

    public static IngestReadingError of(
            int index,
            String tagCode,
            String externalAddress,
            String code,
            String message,
            List<FieldErrorDetail> fieldErrors) {
        return new IngestReadingError(
                index, tagCode, externalAddress, code, message, fieldErrors == null ? List.of() : fieldErrors);
    }
}
