package com.factory.central.cache;

/** Test / fallback sink that drops all live events. */
public enum NoOpTelemetryStreamSink implements TelemetryStreamSink {
    INSTANCE;

    @Override
    public void publishTag(LatestTagState state) {
        // no-op
    }

    @Override
    public void publishAlarm(LatestTagState state) {
        // no-op
    }
}
