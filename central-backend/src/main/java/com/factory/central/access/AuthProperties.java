package com.factory.central.access;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    private String sessionCookieName = "SF_SESSION";
    private long sessionTtlSeconds = 28_800L;
    private final BootstrapAdmin bootstrapAdmin = new BootstrapAdmin();
    private String bootstrapEdgeTokens = "";

    public String getSessionCookieName() {
        return sessionCookieName == null || sessionCookieName.isBlank() ? "SF_SESSION" : sessionCookieName.trim();
    }

    public void setSessionCookieName(String sessionCookieName) {
        this.sessionCookieName = sessionCookieName;
    }

    public long getSessionTtlSeconds() {
        return sessionTtlSeconds < 60 ? 28_800L : sessionTtlSeconds;
    }

    public void setSessionTtlSeconds(long sessionTtlSeconds) {
        this.sessionTtlSeconds = sessionTtlSeconds;
    }

    public BootstrapAdmin getBootstrapAdmin() {
        return bootstrapAdmin;
    }

    public String getBootstrapEdgeTokens() {
        return bootstrapEdgeTokens == null ? "" : bootstrapEdgeTokens;
    }

    public void setBootstrapEdgeTokens(String bootstrapEdgeTokens) {
        this.bootstrapEdgeTokens = bootstrapEdgeTokens;
    }

    public static class BootstrapAdmin {
        private String actorId = "";
        private String password = "";
        private String displayName = "Bootstrap Admin";

        public String getActorId() {
            return actorId == null ? "" : actorId.trim();
        }

        public void setActorId(String actorId) {
            this.actorId = actorId;
        }

        public String getPassword() {
            return password == null ? "" : password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getDisplayName() {
            return displayName == null || displayName.isBlank() ? "Bootstrap Admin" : displayName.trim();
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }
    }
}