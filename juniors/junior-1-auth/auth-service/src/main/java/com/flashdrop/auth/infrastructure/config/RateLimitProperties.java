package com.flashdrop.auth.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.ratelimit")
public class RateLimitProperties {

    private int maxAttempts = 5;
    private long windowSeconds = 300;

    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int v) { this.maxAttempts = v; }

    public long getWindowSeconds() { return windowSeconds; }
    public void setWindowSeconds(long v) { this.windowSeconds = v; }
}
