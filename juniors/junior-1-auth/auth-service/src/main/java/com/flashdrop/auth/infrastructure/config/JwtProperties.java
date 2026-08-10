package com.flashdrop.auth.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración de JWT (RS256). En producción se proveen las claves RSA por
 * entorno; si faltan y no se permite clave efímera, el arranque falla (S-02).
 */
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    private String issuer = "flashdrop-auth";
    private long accessExpirationMinutes = 15;
    private long refreshExpirationDays = 30;

    /** Claves RSA en PEM. Vacías = generar efímera (solo si allowEphemeralKey). */
    private String privateKey = "";
    private String publicKey = "";
    private boolean allowEphemeralKey = false;

    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }

    public long getAccessExpirationMinutes() { return accessExpirationMinutes; }
    public void setAccessExpirationMinutes(long v) { this.accessExpirationMinutes = v; }

    public long getRefreshExpirationDays() { return refreshExpirationDays; }
    public void setRefreshExpirationDays(long v) { this.refreshExpirationDays = v; }

    public String getPrivateKey() { return privateKey; }
    public void setPrivateKey(String v) { this.privateKey = v; }

    public String getPublicKey() { return publicKey; }
    public void setPublicKey(String v) { this.publicKey = v; }

    public boolean isAllowEphemeralKey() { return allowEphemeralKey; }
    public void setAllowEphemeralKey(boolean v) { this.allowEphemeralKey = v; }
}
