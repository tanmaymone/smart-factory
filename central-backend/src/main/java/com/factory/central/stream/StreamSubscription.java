package com.factory.central.stream;

import com.factory.central.cache.LatestTagState;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

final class StreamSubscription {

    private final StreamSession session;
    private final String siteId;
    private final String lineId;
    private final AtomicReference<LatestTagState> pendingTag = new AtomicReference<>();
    private final AtomicReference<LatestTagState> pendingAlarm = new AtomicReference<>();
    private final AtomicReference<CommandUpdateEvent> pendingCommand = new AtomicReference<>();
    private final AtomicBoolean heartbeatRequested = new AtomicBoolean(false);
    private final AtomicBoolean snapshotRequested = new AtomicBoolean(false);
    private final AtomicBoolean removed = new AtomicBoolean(false);
    private final ReentrantLock sendLock = new ReentrantLock();

    StreamSubscription(StreamSession session, String siteId, String lineId) {
        this.session = session;
        this.siteId = blankToNull(siteId);
        this.lineId = blankToNull(lineId);
    }

    StreamSession session() {
        return session;
    }

    String siteId() {
        return siteId;
    }

    String lineId() {
        return lineId;
    }

    boolean matches(String eventSiteId, String eventLineId) {
        if (siteId != null && !siteId.equals(eventSiteId)) {
            return false;
        }
        return lineId == null || lineId.equals(eventLineId);
    }

    void setPendingTag(LatestTagState state) {
        pendingTag.set(state);
    }

    void setPendingAlarm(LatestTagState state) {
        pendingAlarm.set(state);
    }

    void setPendingCommand(CommandUpdateEvent event) {
        pendingCommand.set(event);
    }

    void requestHeartbeat() {
        heartbeatRequested.set(true);
    }

    void requestSnapshot() {
        snapshotRequested.set(true);
    }

    LatestTagState takePendingTag() {
        return pendingTag.getAndSet(null);
    }

    LatestTagState takePendingAlarm() {
        return pendingAlarm.getAndSet(null);
    }

    CommandUpdateEvent takePendingCommand() {
        return pendingCommand.getAndSet(null);
    }

    boolean takeHeartbeat() {
        return heartbeatRequested.getAndSet(false);
    }

    boolean takeSnapshot() {
        return snapshotRequested.getAndSet(false);
    }

    boolean hasPending() {
        return pendingTag.get() != null
                || pendingAlarm.get() != null
                || pendingCommand.get() != null
                || heartbeatRequested.get()
                || snapshotRequested.get();
    }

    boolean tryLockSend() {
        return sendLock.tryLock();
    }

    void unlockSend() {
        sendLock.unlock();
    }

    boolean markRemoved() {
        return removed.compareAndSet(false, true);
    }

    boolean isRemoved() {
        return removed.get();
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
