package com.factory.central.stream;

import com.factory.central.cache.LatestTagCache;
import com.factory.central.cache.LatestTagState;
import com.factory.central.ingestion.LatestTagStateResponse;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

final class StreamDispatch {

    private final LatestTagCache latestTagCache;
    private final AtomicLong eventIds;
    private final Consumer<StreamSubscription> remove;

    StreamDispatch(LatestTagCache latestTagCache, AtomicLong eventIds, Consumer<StreamSubscription> remove) {
        this.latestTagCache = latestTagCache;
        this.eventIds = eventIds;
        this.remove = remove;
    }

    void drain(StreamSubscription subscription) {
        StreamSession session = subscription.session();
        try {
            while (!subscription.isRemoved()) {
                boolean snapshot = subscription.takeSnapshot();
                boolean heartbeat = subscription.takeHeartbeat();
                LatestTagState tag = subscription.takePendingTag();
                LatestTagState alarm = subscription.takePendingAlarm();
                CommandUpdateEvent command = subscription.takePendingCommand();
                if (!snapshot && !heartbeat && tag == null && alarm == null && command == null) {
                    break;
                }
                if (snapshot) {
                    writeSnapshot(session, subscription);
                }
                if (heartbeat) {
                    session.sendComment("ping");
                }
                if (tag != null) {
                    sendTag(session, tag);
                }
                if (alarm != null) {
                    sendAlarm(session, alarm);
                }
                if (command != null) {
                    session.sendEvent(nextId(), TelemetryStreamHub.EVENT_COMMAND, command);
                }
            }
        } catch (IOException | RuntimeException ex) {
            remove.accept(subscription);
        }
    }

    private void writeSnapshot(StreamSession session, StreamSubscription subscription) throws IOException {
        session.sendComment("connected");
        List<LatestTagState> latest = latestTagCache.find(subscription.siteId(), subscription.lineId(), null);
        for (LatestTagState state : latest) {
            sendTag(session, state);
        }
        List<LatestTagState> alarms = latestTagCache.findAlarms(subscription.siteId(), subscription.lineId());
        for (LatestTagState state : alarms) {
            sendAlarm(session, state);
        }
    }

    private void sendTag(StreamSession session, LatestTagState state) throws IOException {
        session.sendEvent(nextId(), TelemetryStreamHub.EVENT_TAG_UPDATE, LatestTagStateResponse.from(state));
    }

    private void sendAlarm(StreamSession session, LatestTagState state) throws IOException {
        session.sendEvent(nextId(), TelemetryStreamHub.EVENT_ALARM, LatestTagStateResponse.from(state));
    }

    private String nextId() {
        return Long.toString(eventIds.incrementAndGet());
    }
}
