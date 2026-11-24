package com.factory.central.registry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class TagBatchCsvParser {

    private static final Set<String> REQUIRED = Set.of(
            "siteId",
            "lineId",
            "tagCode",
            "description",
            "protocol",
            "endpointUrl",
            "externalAddress",
            "dataType",
            "writable",
            "deadband");

    private static final Set<String> OPTIONAL = Set.of(
            "unit",
            "minValue",
            "maxValue",
            "warningLow",
            "warningHigh",
            "criticalLow",
            "criticalHigh",
            "interlockRuleKey",
            "lifecycle");

    private TagBatchCsvParser() {}

    static ParseResult parse(String body) {
        if (body == null) {
            return ParseResult.failure(headerRequired());
        }
        String text = stripBom(body);
        if (isBlankBody(text)) {
            return ParseResult.failure(headerRequired());
        }

        List<TagBatchCsvLexer.RawRecord> records;
        try {
            records = TagBatchCsvLexer.splitRecords(text);
        } catch (TagBatchCsvLexer.ParseFailure ex) {
            return ParseResult.failure(ex.errors());
        }

        int headerIndex = -1;
        for (int i = 0; i < records.size(); i++) {
            if (!isBlankRecord(records.get(i).cells())) {
                headerIndex = i;
                break;
            }
        }
        if (headerIndex < 0) {
            return ParseResult.failure(headerRequired());
        }

        TagBatchCsvLexer.RawRecord headerRecord = records.get(headerIndex);
        List<String> headers = new ArrayList<>();
        for (String cell : headerRecord.cells()) {
            headers.add(cell == null ? "" : cell.trim());
        }

        List<RegistryErrorResponse.FieldErrorDetail> headerErrors = validateHeaders(headers);
        if (!headerErrors.isEmpty()) {
            return ParseResult.failure(headerErrors);
        }

        List<TagBatchRowInput> rows = new ArrayList<>();
        for (int i = headerIndex + 1; i < records.size(); i++) {
            TagBatchCsvLexer.RawRecord record = records.get(i);
            if (isBlankRecord(record.cells())) {
                continue;
            }
            boolean exceeded = record.cells().size() > headers.size();
            Map<String, String> fields = new LinkedHashMap<>();
            for (int c = 0; c < headers.size(); c++) {
                String value = c < record.cells().size() ? record.cells().get(c) : "";
                fields.put(headers.get(c), value);
            }
            rows.add(new TagBatchRowInput(fields, record.sourceLine(), exceeded));
        }
        return ParseResult.success(rows);
    }

    private static List<RegistryErrorResponse.FieldErrorDetail> validateHeaders(List<String> headers) {
        List<RegistryErrorResponse.FieldErrorDetail> errors = new ArrayList<>();
        if (headers.isEmpty() || headers.stream().allMatch(h -> h == null || h.isBlank())) {
            return headerRequired();
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String header : headers) {
            if (header == null || header.isBlank()) {
                errors.add(new RegistryErrorResponse.FieldErrorDetail("header", "header name is blank"));
                continue;
            }
            if (!seen.add(header)) {
                errors.add(new RegistryErrorResponse.FieldErrorDetail(header, "duplicate header"));
            }
            if (!REQUIRED.contains(header) && !OPTIONAL.contains(header)) {
                errors.add(new RegistryErrorResponse.FieldErrorDetail(header, "unknown header"));
            }
        }
        for (String required : REQUIRED) {
            if (!seen.contains(required)) {
                errors.add(new RegistryErrorResponse.FieldErrorDetail(required, "required header is missing"));
            }
        }
        return errors;
    }

    private static boolean isBlankRecord(List<String> cells) {
        if (cells.isEmpty()) {
            return true;
        }
        for (String cell : cells) {
            if (cell != null && !cell.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static boolean isBlankBody(String text) {
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch != ' ' && ch != '\t' && ch != '\r' && ch != '\n') {
                return false;
            }
        }
        return true;
    }

    private static String stripBom(String body) {
        if (body.startsWith("\uFEFF")) {
            return body.substring(1);
        }
        return body;
    }

    private static List<RegistryErrorResponse.FieldErrorDetail> headerRequired() {
        return List.of(new RegistryErrorResponse.FieldErrorDetail("csv", "CSV header is required"));
    }

    record ParseResult(boolean ok, List<TagBatchRowInput> rows, List<RegistryErrorResponse.FieldErrorDetail> errors) {
        static ParseResult success(List<TagBatchRowInput> rows) {
            return new ParseResult(true, rows, List.of());
        }

        static ParseResult failure(List<RegistryErrorResponse.FieldErrorDetail> errors) {
            return new ParseResult(false, List.of(), errors);
        }

        String message() {
            if (errors.isEmpty()) {
                return "CSV parse failed";
            }
            String first = errors.getFirst().message();
            if ("CSV header is required".equals(first)) {
                return first;
            }
            return "CSV validation failed";
        }
    }
}
