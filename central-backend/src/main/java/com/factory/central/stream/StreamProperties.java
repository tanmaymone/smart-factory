package com.factory.central.stream;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.stream")
public class StreamProperties {

    private long emitterTimeoutMs = 1_800_000L;
    private long heartbeatMs = 15_000L;
    private int maxSubscribers = 32;
    private int dispatchPoolSize = 2;
    private int dispatchQueueCapacity = 64;

    public long getEmitterTimeoutMs() {
        return emitterTimeoutMs;
    }

    public void setEmitterTimeoutMs(long emitterTimeoutMs) {
        this.emitterTimeoutMs = emitterTimeoutMs;
    }

    public long getHeartbeatMs() {
        return heartbeatMs;
    }

    public void setHeartbeatMs(long heartbeatMs) {
        this.heartbeatMs = heartbeatMs;
    }

    public int getMaxSubscribers() {
        return maxSubscribers;
    }

    public void setMaxSubscribers(int maxSubscribers) {
        this.maxSubscribers = maxSubscribers;
    }

    public int getDispatchPoolSize() {
        return dispatchPoolSize;
    }

    public void setDispatchPoolSize(int dispatchPoolSize) {
        this.dispatchPoolSize = dispatchPoolSize;
    }

    public int getDispatchQueueCapacity() {
        return dispatchQueueCapacity;
    }

    public void setDispatchQueueCapacity(int dispatchQueueCapacity) {
        this.dispatchQueueCapacity = dispatchQueueCapacity;
    }
}
