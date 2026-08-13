package com.flashdrop.auth.application.port.outbound;

/**
 * S-08. Implementación en memoria por ahora; en despliegue distribuido
 * se cambia por una basada en Redis sin tocar el caso de uso.
 */
public interface RateLimiter {
    void checkAllowed(String key);   // lanza RateLimitExceededException si se excede
    void recordFailure(String key);
    void reset(String key);
}
