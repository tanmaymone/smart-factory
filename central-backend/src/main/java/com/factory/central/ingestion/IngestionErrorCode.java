package com.factory.central.ingestion;

public final class IngestionErrorCode {

    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String INGEST_REJECTED = "INGEST_REJECTED";
    public static final String TAG_NOT_FOUND = "TAG_NOT_FOUND";
    public static final String AMBIGUOUS_ADDRESS = "AMBIGUOUS_ADDRESS";
    public static final String INVALID_VALUE = "INVALID_VALUE";
    public static final String TOPIC_PAYLOAD_MISMATCH = "TOPIC_PAYLOAD_MISMATCH";
    public static final String INGEST_BUFFER_FULL = "INGEST_BUFFER_FULL";
    public static final String INVALID_PARAMETER = "INVALID_PARAMETER";

    private IngestionErrorCode() {}
}
