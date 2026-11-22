package com.factory.central.stream;

import java.io.IOException;

/** Abstraction over {@code SseEmitter} so the hub can be unit-tested without a servlet. */
public interface StreamSession {

    void sendEvent(String id, String eventName, Object data) throws IOException;

    void sendComment(String comment) throws IOException;

    void complete();

    void completeWithError(Throwable error);
}
