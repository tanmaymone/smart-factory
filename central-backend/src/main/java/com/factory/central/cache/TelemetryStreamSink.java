package com.factory.central.cache;

/**
 * Non-blocking live-push sink for tag and alarm updates. Implemented by the stream module;
 * ingest must not call {@code SseEmitter.send} on the caller thread.
 */
public interface TelemetryStreamSink {

    void publishTag(LatestTagState state);

    void publishAlarm(LatestTagState state);
}
