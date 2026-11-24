package com.factory.central.access;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.bootstrap")
public class BootstrapProperties {

    private String token = "";
    private String actorId = "bootstrap-admin";
    private String displayName = "Bootstrap Admin";

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token == null ? "" : token;
    }

    public String getActorId() {
        return actorId;
    }

    public void setActorId(String actorId) {
        this.actorId = actorId == null || actorId.isBlank() ? "bootstrap-admin" : actorId.trim();
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName == null || displayName.isBlank() ? "Bootstrap Admin" : displayName.trim();
    }
}
