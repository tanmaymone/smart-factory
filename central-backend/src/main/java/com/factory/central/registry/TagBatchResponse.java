package com.factory.central.registry;

import java.util.List;

public record TagBatchResponse(
        int received, int created, int updated, int failed, List<TagBatchRowResult> rows) {}
