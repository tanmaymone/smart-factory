package com.factory.edge.config;

import java.time.Instant;

/** Immutable sync-owned status snapshot (torn-read free via AtomicReference). */
public record EdgeSyncView(
        Long fetchedVersion,
        String contentHash,
        String source,
        Instant lastSyncedAt,
        Instant lastAttemptAt,
        String lastError) {

    public static final String SOURCE_NONE = "NONE";
    public static final String SOURCE_CENTRAL = "CENTRAL";
    public static final String SOURCE_LOCAL_SNAPSHOT = "LOCAL_SNAPSHOT";
    public static final String SOURCE_STATIC_YAML = "STATIC_YAML";

    public static EdgeSyncView empty() {
        return new EdgeSyncView(null, null, SOURCE_NONE, null, null, null);
    }

    public EdgeSyncView withSource(String source) {
        return new EdgeSyncView(fetchedVersion, contentHash, source, lastSyncedAt, lastAttemptAt, lastError);
    }

    public EdgeSyncView withError(String error, Instant attemptAt) {
        return new EdgeSyncView(fetchedVersion, contentHash, source, lastSyncedAt, attemptAt, error);
    }

    public EdgeSyncView withFetch(
            long version, String hash, String source, Instant syncedAt, Instant attemptAt) {
        return new EdgeSyncView(version, hash, source, syncedAt, attemptAt, null);
    }
}
