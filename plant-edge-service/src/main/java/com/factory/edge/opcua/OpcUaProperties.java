package com.factory.edge.opcua;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.opcua")
public class OpcUaProperties {

    private String endpointUrl = "opc.tcp://localhost:53530/OPCUA/SimulationServer";
    private long requestTimeoutMs = 5000;
    private double publishingIntervalMs = 500;
    private int monitoredItemQueueSize = 10;
    private long reconnectInitialMs = 1000;
    private long reconnectMaxMs = 30000;
    private List<TagConfig> tags = new ArrayList<>();

    @PostConstruct
    void validate() {
        if (reconnectInitialMs < 1) {
            throw new IllegalStateException("app.opcua.reconnect-initial-ms must be >= 1");
        }
        if (reconnectMaxMs < reconnectInitialMs) {
            throw new IllegalStateException(
                    "app.opcua.reconnect-max-ms must be >= reconnect-initial-ms");
        }
        for (TagConfig tag : tags) {
            if (tag.getDeadband() < 0.0) {
                throw new IllegalStateException(
                        "app.opcua.tags deadband must be >= 0 for tagCode="
                                + tag.getTagCode()
                                + " siteId="
                                + tag.getSiteId()
                                + " lineId="
                                + tag.getLineId()
                                + " but was "
                                + tag.getDeadband());
            }
        }
    }

    public String getEndpointUrl() {
        return endpointUrl;
    }

    public void setEndpointUrl(String endpointUrl) {
        this.endpointUrl = endpointUrl;
    }

    public long getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    public void setRequestTimeoutMs(long requestTimeoutMs) {
        this.requestTimeoutMs = requestTimeoutMs;
    }

    public double getPublishingIntervalMs() {
        return publishingIntervalMs;
    }

    public void setPublishingIntervalMs(double publishingIntervalMs) {
        this.publishingIntervalMs = publishingIntervalMs;
    }

    public int getMonitoredItemQueueSize() {
        return monitoredItemQueueSize;
    }

    public void setMonitoredItemQueueSize(int monitoredItemQueueSize) {
        this.monitoredItemQueueSize = monitoredItemQueueSize;
    }

    public long getReconnectInitialMs() {
        return reconnectInitialMs;
    }

    public void setReconnectInitialMs(long reconnectInitialMs) {
        this.reconnectInitialMs = reconnectInitialMs;
    }

    public long getReconnectMaxMs() {
        return reconnectMaxMs;
    }

    public void setReconnectMaxMs(long reconnectMaxMs) {
        this.reconnectMaxMs = reconnectMaxMs;
    }

    public List<TagConfig> getTags() {
        return tags;
    }

    public void setTags(List<TagConfig> tags) {
        this.tags = tags == null ? new ArrayList<>() : tags;
    }

    public static class TagConfig {
        private String siteId;
        private String lineId;
        private String tagCode;
        private String nodeId;
        private double deadband;

        public String getSiteId() {
            return siteId;
        }

        public void setSiteId(String siteId) {
            this.siteId = siteId;
        }

        public String getLineId() {
            return lineId;
        }

        public void setLineId(String lineId) {
            this.lineId = lineId;
        }

        public String getTagCode() {
            return tagCode;
        }

        public void setTagCode(String tagCode) {
            this.tagCode = tagCode;
        }

        public String getNodeId() {
            return nodeId;
        }

        public void setNodeId(String nodeId) {
            this.nodeId = nodeId;
        }

        public double getDeadband() {
            return deadband;
        }

        public void setDeadband(double deadband) {
            this.deadband = deadband;
        }
    }
}
