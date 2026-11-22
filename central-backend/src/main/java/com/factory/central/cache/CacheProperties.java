package com.factory.central.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cache")
public class CacheProperties {

    /**
     * {@code memory} (default) or {@code redis}.
     */
    private String mode = "memory";

    private String redisHost = "localhost";

    private int redisPort = 6379;

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getRedisHost() {
        return redisHost;
    }

    public void setRedisHost(String redisHost) {
        this.redisHost = redisHost;
    }

    public int getRedisPort() {
        return redisPort;
    }

    public void setRedisPort(int redisPort) {
        this.redisPort = redisPort;
    }

    public boolean isRedis() {
        return "redis".equalsIgnoreCase(mode);
    }
}
