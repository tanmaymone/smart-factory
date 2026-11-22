package com.factory.edge.buffer;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.buffer")
public class BufferProperties {

    private String path = "./data/edge-outbound.db";
    private int maxRecords = 10000;
    private int drainBatchSize = 32;
    private long drainPauseMs = 10;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public int getMaxRecords() {
        return maxRecords;
    }

    public void setMaxRecords(int maxRecords) {
        this.maxRecords = maxRecords;
    }

    public int getDrainBatchSize() {
        return drainBatchSize;
    }

    public void setDrainBatchSize(int drainBatchSize) {
        this.drainBatchSize = drainBatchSize;
    }

    public long getDrainPauseMs() {
        return drainPauseMs;
    }

    public void setDrainPauseMs(long drainPauseMs) {
        this.drainPauseMs = drainPauseMs;
    }
}
