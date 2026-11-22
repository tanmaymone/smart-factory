package com.factory.central.stream;

import java.io.IOException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

final class SseEmitterSession implements StreamSession {

    private final SseEmitter emitter;

    SseEmitterSession(SseEmitter emitter) {
        this.emitter = emitter;
    }

    SseEmitter emitter() {
        return emitter;
    }

    @Override
    public void sendEvent(String id, String eventName, Object data) throws IOException {
        emitter.send(SseEmitter.event().id(id).name(eventName).data(data).reconnectTime(3000L));
    }

    @Override
    public void sendComment(String comment) throws IOException {
        emitter.send(SseEmitter.event().comment(comment));
    }

    @Override
    public void complete() {
        emitter.complete();
    }

    @Override
    public void completeWithError(Throwable error) {
        emitter.completeWithError(error);
    }
}
