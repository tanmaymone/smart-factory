package com.factory.central.registry;

import java.util.List;
import java.util.UUID;

public record TagBatchRowResult(
        int index,
        Integer sourceLine,
        String tagCode,
        TagBatchRowOutcome outcome,
        UUID id,
        List<TagBatchRowError> errors) {

    public static TagBatchRowResult of(
            int index,
            Integer sourceLine,
            String tagCode,
            TagBatchRowOutcome outcome,
            UUID id,
            List<TagBatchRowError> errors) {
        return new TagBatchRowResult(
                index, sourceLine, tagCode, outcome, id, errors == null ? List.of() : List.copyOf(errors));
    }
}
