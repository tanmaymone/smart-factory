package com.factory.central.stream;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

final class RecordingStreamSession implements StreamSession {

    record SentEvent(String id, String eventName, Object data) {}

    private final List<SentEvent> events = new CopyOnWriteArrayList<>();
    private final List<String> comments = new CopyOnWriteArrayList<>();
    private final AtomicInteger completeCount = new AtomicInteger();
    private volatile CountDownLatch blockSend;
    private volatile CountDownLatch enteredSend;
    private volatile IOException failWith;

    List<SentEvent> events() {
        return List.copyOf(events);
    }

    List<String> comments() {
        return List.copyOf(comments);
    }

    List<SentEvent> eventsNamed(String name) {
        List<SentEvent> matched = new ArrayList<>();
        for (SentEvent event : events) {
            if (name.equals(event.eventName())) {
                matched.add(event);
            }
        }
        return matched;
    }

    int completeCount() {
        return completeCount.get();
    }

    void failNextSend(IOException error) {
        this.failWith = error;
    }

    void blockSends(CountDownLatch entered, CountDownLatch release) {
        this.enteredSend = entered;
        this.blockSend = release;
    }

    @Override
    public void sendEvent(String id, String eventName, Object data) throws IOException {
        awaitIfBlocked();
        IOException failure = failWith;
        if (failure != null) {
            failWith = null;
            throw failure;
        }
        events.add(new SentEvent(id, eventName, data));
    }

    @Override
    public void sendComment(String comment) throws IOException {
        awaitIfBlocked();
        IOException failure = failWith;
        if (failure != null) {
            failWith = null;
            throw failure;
        }
        comments.add(comment);
    }

    @Override
    public void complete() {
        completeCount.incrementAndGet();
    }

    @Override
    public void completeWithError(Throwable error) {
        completeCount.incrementAndGet();
    }

    private void awaitIfBlocked() throws IOException {
        CountDownLatch entered = enteredSend;
        CountDownLatch release = blockSend;
        if (entered != null) {
            entered.countDown();
        }
        if (release != null) {
            try {
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new IOException("send block latch timed out");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IOException("send blocked interrupted", ex);
            }
        }
    }
}
