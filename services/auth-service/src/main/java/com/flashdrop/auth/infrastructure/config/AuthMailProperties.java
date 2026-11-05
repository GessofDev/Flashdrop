package com.flashdrop.auth.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.mail")
public class AuthMailProperties {

    private boolean enabled = false;
    private String host = "";
    private int port = 587;
    private String username = "";
    private String password = "";
    private String from = "";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { this.enabled = v; }

    public String getHost() { return host; }
    public void setHost(String v) { this.host = v; }

    public int getPort() { return port; }
    public void setPort(int v) { this.port = v; }

    public String getUsername() { return username; }
    public void setUsername(String v) { this.username = v; }

    public String getPassword() { return password; }
    public void setPassword(String v) { this.password = v; }

    public String getFrom() { return from; }
    public void setFrom(String v) { this.from = v; }
}
