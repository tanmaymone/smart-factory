package com.factory.central.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class TelemetryPayloadParser {

    private TelemetryPayloadParser() {}

    public static ParsedPayload parse(JsonNode root) {
        if (root == null || root.isNull() || !root.isObject()) {
            throw new PayloadParseException(IngestionErrorCode.VALIDATION_FAILED, "Request body must be a JSON object");
        }

        if (root.has("readings")) {
            return parseEnvelope(root);
        }
        return parseSingleReading(root);
    }

    private static ParsedPayload parseEnvelope(JsonNode root) {
        String siteId = textOrNull(root.get("siteId"));
        String lineId = textOrNull(root.get("lineId"));
        String messageId = textOrNull(root.get("messageId"));
        Instant envelopeTimestamp = parseOptionalTimestamp(root.get("timestamp"), "timestamp");

        if (siteId == null || siteId.isBlank() || lineId == null || lineId.isBlank()) {
            throw new PayloadParseException(
                    IngestionErrorCode.VALIDATION_FAILED, "siteId and lineId are required on telemetry envelope");
        }

        JsonNode readingsNode = root.get("readings");
        if (readingsNode == null || !readingsNode.isArray()) {
            throw new PayloadParseException(IngestionErrorCode.VALIDATION_FAILED, "readings must be a JSON array");
        }

        List<RawReading> readings = new ArrayList<>();
        for (int i = 0; i < readingsNode.size(); i++) {
            readings.add(parseReadingNode(readingsNode.get(i), i));
        }
        return new ParsedPayload(messageId, siteId, lineId, envelopeTimestamp, readings);
    }

    private static ParsedPayload parseSingleReading(JsonNode root) {
        String siteId = textOrNull(root.get("siteId"));
        String lineId = textOrNull(root.get("lineId"));
        if (siteId == null || siteId.isBlank() || lineId == null || lineId.isBlank()) {
            throw new PayloadParseException(
                    IngestionErrorCode.VALIDATION_FAILED, "siteId and lineId are required on telemetry reading");
        }
        Instant envelopeTimestamp = parseOptionalTimestamp(root.get("timestamp"), "timestamp");
        RawReading reading = parseReadingNode(root, 0);
        return new ParsedPayload(textOrNull(root.get("messageId")), siteId, lineId, envelopeTimestamp, List.of(reading));
    }

    private static RawReading parseReadingNode(JsonNode node, int index) {
        if (node == null || !node.isObject()) {
            throw new PayloadParseException(
                    IngestionErrorCode.VALIDATION_FAILED, "Reading at index " + index + " must be a JSON object");
        }
        return new RawReading(
                index,
                textOrNull(node.get("tagCode")),
                textOrNull(node.get("externalAddress")),
                node.get("value"),
                textOrNull(node.get("quality")),
                textOrNull(node.get("sourceTimestamp")),
                textOrNull(node.get("dataType")),
                booleanOrNull(node.get("nodeWritable")));
    }

    static String observedDataType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "FLOAT", "INT", "BOOLEAN", "STRING" -> normalized;
            default -> null;
        };
    }

    private static Instant parseOptionalTimestamp(JsonNode node, String field) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw new PayloadParseException(
                    IngestionErrorCode.INVALID_VALUE, field + " must be an ISO-8601 timestamp with offset or Z");
        }
        String raw = node.asText();
        if (TimestampParser.isZoneLessLocalDateTime(raw)) {
            throw new PayloadParseException(
                    IngestionErrorCode.INVALID_VALUE, field + " must include Z or a numeric offset");
        }
        Optional<Instant> parsed = TimestampParser.parseInstant(raw);
        if (parsed.isEmpty()) {
            throw new PayloadParseException(
                    IngestionErrorCode.INVALID_VALUE, field + " must be an ISO-8601 timestamp with offset or Z");
        }
        return parsed.get();
    }

    static Instant resolveReadingTimestamp(RawReading reading, Instant envelopeTimestamp, Instant serverNow) {
        if (reading.sourceTimestamp() != null && !reading.sourceTimestamp().isBlank()) {
            if (TimestampParser.isZoneLessLocalDateTime(reading.sourceTimestamp())) {
                throw new ReadingRejectException(
                        IngestionErrorCode.INVALID_VALUE,
                        "sourceTimestamp must include Z or a numeric offset",
                        List.of(new IngestReadingError.FieldErrorDetail(
                                "sourceTimestamp", "must include Z or a numeric offset")));
            }
            return TimestampParser.parseInstant(reading.sourceTimestamp())
                    .orElseThrow(() -> new ReadingRejectException(
                            IngestionErrorCode.INVALID_VALUE,
                            "sourceTimestamp must be an ISO-8601 timestamp with offset or Z",
                            List.of(new IngestReadingError.FieldErrorDetail(
                                    "sourceTimestamp", "must be ISO-8601 with offset or Z"))));
        }
        if (envelopeTimestamp != null) {
            return envelopeTimestamp;
        }
        return serverNow;
    }

    private static Boolean booleanOrNull(JsonNode node) {
        if (node == null || node.isNull() || !node.isBoolean()) {
            return null;
        }
        return node.asBoolean();
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual() && !node.isNumber() && !node.isBoolean()) {
            return null;
        }
        return node.asText();
    }

    public record ParsedPayload(
            String messageId, String siteId, String lineId, Instant envelopeTimestamp, List<RawReading> readings) {}

    public record RawReading(
            int index,
            String tagCode,
            String externalAddress,
            JsonNode value,
            String quality,
            String sourceTimestamp,
            String dataType,
            Boolean nodeWritable) {}

    public static final class PayloadParseException extends RuntimeException {
        private final String code;

        public PayloadParseException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    public static final class ReadingRejectException extends RuntimeException {
        private final String code;
        private final List<IngestReadingError.FieldErrorDetail> fieldErrors;

        public ReadingRejectException(
                String code, String message, List<IngestReadingError.FieldErrorDetail> fieldErrors) {
            super(message);
            this.code = code;
            this.fieldErrors = fieldErrors == null ? List.of() : fieldErrors;
        }

        public String getCode() {
            return code;
        }

        public List<IngestReadingError.FieldErrorDetail> getFieldErrors() {
            return fieldErrors;
        }
    }
}
