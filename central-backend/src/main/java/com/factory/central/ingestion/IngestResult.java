package com.factory.central.ingestion;

import java.util.List;

public record IngestResult(int accepted, List<IngestReadingError> errors, boolean bufferFull) {

    public static IngestResult of(int accepted, List<IngestReadingError> errors) {
        return new IngestResult(accepted, errors, false);
    }

    public static IngestResult bufferFull(List<IngestReadingError> errors) {
        return new IngestResult(0, errors, true);
    }
}
