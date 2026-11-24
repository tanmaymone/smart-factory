package com.factory.central.registry;

import java.util.ArrayList;
import java.util.List;

final class TagBatchCsvLexer {

    private TagBatchCsvLexer() {}

    static List<RawRecord> splitRecords(String text) throws ParseFailure {
        List<RawRecord> records = new ArrayList<>();
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean inQuotes = false;
        int sourceLine = 1;
        int recordStartLine = 1;
        int i = 0;
        while (i < text.length()) {
            char ch = text.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        cell.append('"');
                        i += 2;
                        continue;
                    }
                    inQuotes = false;
                    i++;
                    continue;
                }
                if (ch == '\r') {
                    cell.append('\r');
                    if (i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                        cell.append('\n');
                        i += 2;
                    } else {
                        i++;
                    }
                    sourceLine++;
                    continue;
                }
                if (ch == '\n') {
                    cell.append('\n');
                    sourceLine++;
                    i++;
                    continue;
                }
                cell.append(ch);
                i++;
                continue;
            }

            if (ch == '"') {
                inQuotes = true;
                i++;
                continue;
            }
            if (ch == ',') {
                cells.add(cell.toString());
                cell.setLength(0);
                i++;
                continue;
            }
            if (ch == '\r' || ch == '\n') {
                cells.add(cell.toString());
                cell.setLength(0);
                records.add(new RawRecord(recordStartLine, cells));
                cells = new ArrayList<>();
                if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i += 2;
                } else {
                    i++;
                }
                sourceLine++;
                recordStartLine = sourceLine;
                continue;
            }
            cell.append(ch);
            i++;
        }
        if (inQuotes) {
            throw new ParseFailure(List.of(new RegistryErrorResponse.FieldErrorDetail("csv", "unclosed quote")));
        }
        if (cell.length() > 0 || !cells.isEmpty()) {
            cells.add(cell.toString());
            records.add(new RawRecord(recordStartLine, cells));
        }
        return records;
    }

    record RawRecord(int sourceLine, List<String> cells) {}

    static final class ParseFailure extends Exception {
        private final List<RegistryErrorResponse.FieldErrorDetail> errors;

        ParseFailure(List<RegistryErrorResponse.FieldErrorDetail> errors) {
            this.errors = errors;
        }

        List<RegistryErrorResponse.FieldErrorDetail> errors() {
            return errors;
        }
    }
}
