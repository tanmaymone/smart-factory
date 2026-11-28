package com.factory.edge.config;

import jakarta.annotation.PostConstruct;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.edge")
public class EdgeConfigProperties {

    static final Pattern SITE_ID_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$");

    private boolean configSyncEnabled = false;
    private String siteId = "INDORE";
    private String centralBaseUrl = "http://localhost:8080";
    private long configPollMs = 15_000L;
    private long configTimeoutMs = 5_000L;
    private String snapshotPath = "./data/edge-config.json";
    private String centralCaCertPath = "";

    @PostConstruct
    void validate() {
        if (!configSyncEnabled) {
            return;
        }
        String normalizedSiteId = getNormalizedSiteId();
        if (normalizedSiteId.isEmpty() || !SITE_ID_PATTERN.matcher(normalizedSiteId).matches()) {
            throw new IllegalStateException(
                    "app.edge.site-id must match ^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$ when"
                            + " config-sync-enabled=true but was '"
                            + siteId
                            + "'");
        }
        if (centralBaseUrl == null || centralBaseUrl.isBlank()) {
            throw new IllegalStateException("app.edge.central-base-url must be set when config-sync-enabled=true");
        }
        if (configPollMs < 1_000L) {
            throw new IllegalStateException("app.edge.config-poll-ms must be >= 1000");
        }
        if (configTimeoutMs < 100L) {
            throw new IllegalStateException("app.edge.config-timeout-ms must be >= 100");
        }
        if (snapshotPath == null || snapshotPath.isBlank()) {
            throw new IllegalStateException("app.edge.snapshot-path must be set when config-sync-enabled=true");
        }
        String ca = centralCaCertPath == null ? "" : centralCaCertPath.trim();
        if (!ca.isEmpty() && !java.nio.file.Files.isRegularFile(java.nio.file.Path.of(ca))) {
            throw new IllegalStateException("app.edge.central-ca-cert-path is not readable: " + ca);
        }
    }

    /** Trimmed site id used by HTTP, snapshot, and MQTT consumers. */
    public String getNormalizedSiteId() {
        return siteId == null ? "" : siteId.trim();
    }

    public boolean isConfigSyncEnabled() {
        return configSyncEnabled;
    }

    public void setConfigSyncEnabled(boolean configSyncEnabled) {
        this.configSyncEnabled = configSyncEnabled;
    }

    public String getSiteId() {
        return siteId;
    }

    public void setSiteId(String siteId) {
        this.siteId = siteId;
    }

    public String getCentralBaseUrl() {
        return centralBaseUrl;
    }

    public void setCentralBaseUrl(String centralBaseUrl) {
        this.centralBaseUrl = centralBaseUrl;
    }

    public long getConfigPollMs() {
        return configPollMs;
    }

    public void setConfigPollMs(long configPollMs) {
        this.configPollMs = configPollMs;
    }

    public long getConfigTimeoutMs() {
        return configTimeoutMs;
    }

    public void setConfigTimeoutMs(long configTimeoutMs) {
        this.configTimeoutMs = configTimeoutMs;
    }

    public String getSnapshotPath() {
        return snapshotPath;
    }

    public void setSnapshotPath(String snapshotPath) {
        this.snapshotPath = snapshotPath;
    }

    public String getCentralCaCertPath() {
        return centralCaCertPath;
    }

    public void setCentralCaCertPath(String centralCaCertPath) {
        this.centralCaCertPath = centralCaCertPath == null ? "" : centralCaCertPath;
    }
}
