package com.flashdrop.auth.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.password-reset")
public class PasswordResetProperties {

    private long tokenTtlMinutes = 30;
    private String linkBaseUrl = "";

    public long getTokenTtlMinutes() { return tokenTtlMinutes; }
    public void setTokenTtlMinutes(long v) { this.tokenTtlMinutes = v; }

    public String getLinkBaseUrl() { return linkBaseUrl; }
    public void setLinkBaseUrl(String v) { this.linkBaseUrl = v; }
}
