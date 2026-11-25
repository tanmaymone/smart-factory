package com.factory.central.registry;

import java.time.Instant;
import java.util.List;

public record EdgeConfigManifest(
        String siteId,
        String siteLifecycle,
        long version,
        String contentHash,
        Instant generatedAt,
        List<EdgeConfigLine> lines) {}
