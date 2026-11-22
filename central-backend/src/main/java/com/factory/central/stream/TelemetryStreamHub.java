package com.factory.central.stream;

import com.factory.central.cache.LatestTagCache;
import com.factory.central.cache.LatestTagState;
import com.factory.central.cache.TelemetryStreamSink;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class TelemetryStreamHub implements TelemetryStreamSink {

    static final String EVENT_TAG_UPDATE = "TAG_UPDATE";
    static final String EVENT_ALARM = "ALARM_EVENT";
    static final String EVENT_COMMAND = "COMMAND_UPDATE";

    private final StreamProperties properties;
    private final MeterRegistry meterRegistry;
    private final CopyOnWriteArrayList<StreamSubscription> subscribers = new CopyOnWriteArrayList<>();
    private final Object admissionLock = new Object();
    private final AtomicLong eventIds = new AtomicLong();
    private final ExecutorService dispatcher;
    private final StreamDispatch dispatch;

    public TelemetryStreamHub(
            StreamProperties properties, LatestTagCache latestTagCache, MeterRegistry meterRegistry) {
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.dispatch = new StreamDispatch(latestTagCache, eventIds, this::remove);
        this.dispatcher = new ThreadPoolExecutor(
                properties.getDispatchPoolSize(),
                properties.getDispatchPoolSize(),
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(properties.getDispatchQueueCapacity()),
                r -> {
                    Thread t = new Thread(r, "sse-dispatch");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    public SseEmitter subscribe(String siteId, String lineId) {
        SseEmitter emitter;
        StreamSubscription subscription;
        synchronized (admissionLock) {
            if (subscribers.size() >= properties.getMaxSubscribers()) {
                throw new StreamAtCapacityException(
                        "SSE subscriber limit reached (" + properties.getMaxSubscribers() + ")");
            }
            emitter = new SseEmitter(properties.getEmitterTimeoutMs());
            SseEmitterSession session = new SseEmitterSession(emitter);
            subscription = new StreamSubscription(session, siteId, lineId);
            subscribers.add(subscription);
            StreamSubscription sub = subscription;
            emitter.onCompletion(() -> remove(sub));
            emitter.onTimeout(() -> remove(sub));
            emitter.onError(error -> remove(sub));
        }
        subscription.requestSnapshot();
        scheduleFlush(subscription);
        return emitter;
    }

    /** Test admission / fan-out without a servlet container. */
    StreamSubscription registerSession(StreamSession session, String siteId, String lineId) {
        StreamSubscription subscription;
        synchronized (admissionLock) {
            if (subscribers.size() >= properties.getMaxSubscribers()) {
                throw new StreamAtCapacityException(
                        "SSE subscriber limit reached (" + properties.getMaxSubscribers() + ")");
            }
            subscription = new StreamSubscription(session, siteId, lineId);
            subscribers.add(subscription);
        }
        subscription.requestSnapshot();
        scheduleFlush(subscription);
        return subscription;
    }

    int subscriberCount() {
        return subscribers.size();
    }

    @Override
    public void publishTag(LatestTagState state) {
        Objects.requireNonNull(state, "state");
        fanOut(state.siteId(), state.lineId(), sub -> sub.setPendingTag(state));
    }

    @Override
    public void publishAlarm(LatestTagState state) {
        Objects.requireNonNull(state, "state");
        fanOut(state.siteId(), state.lineId(), sub -> sub.setPendingAlarm(state));
    }

    public void publishCommand(CommandUpdateEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("command event must not be null");
        }
        String status = event.status();
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("command status must not be blank");
        }
        if (!CommandUpdateEvent.Status.ALL.contains(status)) {
            throw new IllegalArgumentException("unknown command status: " + status);
        }
        fanOut(event.siteId(), event.lineId(), sub -> sub.setPendingCommand(event));
    }

    public void heartbeat() {
        for (StreamSubscription sub : subscribers) {
            if (sub.isRemoved()) {
                continue;
            }
            sub.requestHeartbeat();
            scheduleFlush(sub);
        }
    }

    void remove(StreamSubscription subscription) {
        if (!subscription.markRemoved()) {
            return;
        }
        subscribers.remove(subscription);
        try {
            subscription.session().complete();
        } catch (Exception ignored) {
            // already closed
        }
    }

    private void fanOut(String siteId, String lineId, java.util.function.Consumer<StreamSubscription> pending) {
        for (StreamSubscription sub : subscribers) {
            if (sub.isRemoved() || !sub.matches(siteId, lineId)) {
                continue;
            }
            pending.accept(sub);
            scheduleFlush(sub);
        }
    }

    private void scheduleFlush(StreamSubscription subscription) {
        try {
            dispatcher.execute(() -> flush(subscription));
        } catch (Exception ex) {
            meterRegistry.counter("stream.events.dropped").increment();
        }
    }

    private void flush(StreamSubscription subscription) {
        if (subscription.isRemoved() || !subscription.tryLockSend()) {
            return;
        }
        try {
            dispatch.drain(subscription);
        } finally {
            subscription.unlockSend();
        }
        if (!subscription.isRemoved() && subscription.hasPending()) {
            scheduleFlush(subscription);
        }
    }

    @PreDestroy
    void shutdown() {
        dispatcher.shutdownNow();
    }
}
