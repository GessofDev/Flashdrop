package com.flashdrop.reviews.infrastructure.security;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Caches the JWKS published by auth-service and resolves keys by {@code kid}.
 *
 * <p>Espejo de {@code delivery-service/infrastructure/security/JwksKeyProvider}:
 * un único {@link JWKSet} cacheado bajo {@link ReentrantLock}, refrescado bajo
 * demanda cuando el filtro ve un {@code kid} que no reconoce (rotación).</p>
 */
@Component
public class JwksKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(JwksKeyProvider.class);

    private final URL jwksUri;
    private final ReentrantLock refreshLock = new ReentrantLock();
    private volatile JWKSet cached;

    public JwksKeyProvider(@Value("${auth.jwks-uri}") String jwksUri) {
        try {
            this.jwksUri = new URL(jwksUri);
        } catch (MalformedURLException e) {
            throw new IllegalStateException("Invalid auth.jwks-uri: " + jwksUri, e);
        }
        try {
            refresh();
        } catch (RuntimeException e) {
            log.warn("Initial JWKS preload failed (uri={}): {}. Will retry on first request.",
                    jwksUri, e.getMessage());
        }
    }

    /**
     * Look up a key by {@code kid}. Returns {@link Optional#empty()} if the
     * cache does not contain it; the caller is responsible for deciding
     * whether to trigger a refresh (see {@link JwtAuthenticationFilter}).
     */
    public Optional<JWK> findKeyByKid(String kid) {
        JWKSet snapshot = cached;
        if (snapshot == null || kid == null) {
            return Optional.empty();
        }
        JWK key = snapshot.getKeyByKeyId(kid);
        return Optional.ofNullable(key);
    }

    /**
     * Force-refresh the cache from the configured JWKS URI. Thread-safe via
     * {@link ReentrantLock}; concurrent callers block until the first finishes.
     */
    public void refresh() {
        refreshLock.lock();
        try {
            JWKSet fresh = JWKSet.load(jwksUri);
            this.cached = fresh;
            log.info("JWKS refreshed: {} key(s) from {}", fresh.getKeys().size(), jwksUri);
        } catch (IOException | ParseException e) {
            throw new IllegalStateException("Failed to load JWKS from " + jwksUri, e);
        } finally {
            refreshLock.unlock();
        }
    }
}
