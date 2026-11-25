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
    private SecurityConfig security = new SecurityConfig();
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
        security.validate();
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

    public SecurityConfig getSecurity() {
        return security;
    }

    public void setSecurity(SecurityConfig security) {
        this.security = security == null ? new SecurityConfig() : security;
    }

    public List<TagConfig> getTags() {
        return tags;
    }

    public void setTags(List<TagConfig> tags) {
        this.tags = tags == null ? new ArrayList<>() : tags;
    }

    public static class SecurityConfig {
        public static final String POLICY_NONE = "None";
        public static final String POLICY_BASIC256SHA256 = "Basic256Sha256";
        public static final String IDENTITY_ANONYMOUS = "anonymous";

        private String policy = POLICY_NONE;
        private String identity = IDENTITY_ANONYMOUS;
        private String pkiDir = "";

        void validate() {
            String p = policy == null ? "" : policy.trim();
            String id = identity == null ? "" : identity.trim();
            if (POLICY_NONE.equalsIgnoreCase(p)) {
                if (!IDENTITY_ANONYMOUS.equalsIgnoreCase(id)) {
                    throw new IllegalStateException(
                            "app.opcua.security.identity must be anonymous when policy is None");
                }
                return;
            }
            if (POLICY_BASIC256SHA256.equalsIgnoreCase(p)) {
                if (!IDENTITY_ANONYMOUS.equalsIgnoreCase(id)) {
                    throw new IllegalStateException(
                            "app.opcua.security.identity must be anonymous in this release"
                                    + " (username tokens are out of scope)");
                }
                return;
            }
            throw new IllegalStateException(
                    "app.opcua.security.policy must be None or Basic256Sha256 but was " + policy);
        }

        public boolean isNone() {
            return POLICY_NONE.equalsIgnoreCase(policy == null ? "" : policy.trim());
        }

        public boolean isBasic256Sha256() {
            return POLICY_BASIC256SHA256.equalsIgnoreCase(policy == null ? "" : policy.trim());
        }

        public boolean isAnonymous() {
            return IDENTITY_ANONYMOUS.equalsIgnoreCase(identity == null ? "" : identity.trim());
        }

        public String getPolicy() {
            return policy;
        }

        public void setPolicy(String policy) {
            this.policy = policy;
        }

        public String getIdentity() {
            return identity;
        }

        public void setIdentity(String identity) {
            this.identity = identity;
        }

        public String getPkiDir() {
            return pkiDir;
        }

        public void setPkiDir(String pkiDir) {
            this.pkiDir = pkiDir;
        }
    }

    public static class TagConfig {
        private String siteId;
        private String lineId;
        private String tagCode;
        private String nodeId;
        private double deadband;
        private boolean writable = true;
        private String protocol = "OPC_UA";
        private String dataType;
        private String endpointUrl;

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

        public boolean isWritable() {
            return writable;
        }

        public void setWritable(boolean writable) {
            this.writable = writable;
        }

        public String getProtocol() {
            return protocol;
        }

        public void setProtocol(String protocol) {
            this.protocol = protocol;
        }

        public String getDataType() {
            return dataType;
        }

        public void setDataType(String dataType) {
            this.dataType = dataType;
        }

        public String getEndpointUrl() {
            return endpointUrl;
        }

        public void setEndpointUrl(String endpointUrl) {
            this.endpointUrl = endpointUrl;
        }
    }
}
