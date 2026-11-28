package com.factory.central.registry;

import java.util.LinkedHashMap;
import java.util.Map;

/** Lenient row input: string fields so one bad cell does not fail Jackson for the whole body. */
public final class TagBatchRowInput {

    private final Map<String, String> fields;
    private final Integer sourceLine;
    private final boolean columnCountExceeded;

    public TagBatchRowInput(Map<String, String> fields, Integer sourceLine, boolean columnCountExceeded) {
        this.fields = fields == null ? Map.of() : Map.copyOf(fields);
        this.sourceLine = sourceLine;
        this.columnCountExceeded = columnCountExceeded;
    }

    public static TagBatchRowInput fromMap(Map<String, Object> raw) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (raw != null) {
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                if (entry.getValue() == null) {
                    continue;
                }
                fields.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }
        return new TagBatchRowInput(fields, null, false);
    }

    public String get(String key) {
        return fields.get(key);
    }

    public Integer sourceLine() {
        return sourceLine;
    }

    public boolean columnCountExceeded() {
        return columnCountExceeded;
    }

    public Map<String, String> fields() {
        return fields;
    }
}
