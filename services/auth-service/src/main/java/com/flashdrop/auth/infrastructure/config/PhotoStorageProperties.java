package com.flashdrop.auth.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bucket de las fotos de perfil (F10). Las credenciales llegan solo por variables
 * de entorno ({@code .env} del servidor); nunca se escriben en el codigo.
 */
@ConfigurationProperties(prefix = "auth.photo-storage")
public class PhotoStorageProperties {

    private boolean enabled = false;
    private String endpoint = "";
    private String bucket = "flashdrop-avatars";
    private String region = "us-east-1";
    private String accessKey = "";
    private String secretKey = "";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { this.enabled = v; }

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String v) { this.endpoint = v == null ? "" : v; }

    public String getBucket() { return bucket; }
    public void setBucket(String v) { this.bucket = v == null ? "" : v; }

    public String getRegion() { return region; }
    public void setRegion(String v) { this.region = v == null ? "" : v; }

    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String v) { this.accessKey = v == null ? "" : v; }

    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String v) { this.secretKey = v == null ? "" : v; }
}
