package com.flashdrop.auth.infrastructure.adapter.outbound.ratelimit;

import com.flashdrop.auth.application.port.outbound.RateLimiter;
import com.flashdrop.auth.domain.exception.RateLimitExceededException;
import com.flashdrop.auth.infrastructure.config.RateLimitProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiter de ventana fija en memoria (S-08). En despliegue distribuido
 * se reemplaza por una implementación con Redis sin tocar el caso de uso.
 */
@Component
public class InMemoryRateLimiter implements RateLimiter {

    private record Window(int count, long resetAtMillis) {}

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final int maxAttempts;
    private final long windowMillis;

    public InMemoryRateLimiter(RateLimitProperties props) {
        this.maxAttempts = props.getMaxAttempts();
        this.windowMillis = props.getWindowSeconds() * 1000;
    }

    @Override
    public void checkAllowed(String key) {
        Window w = windows.get(key);
        if (w != null && System.currentTimeMillis() < w.resetAtMillis() && w.count() >= maxAttempts) {
            throw new RateLimitExceededException();
        }
    }

    @Override
    public void recordFailure(String key) {
        long now = System.currentTimeMillis();
        windows.compute(key, (k, w) -> (w == null || now >= w.resetAtMillis())
                ? new Window(1, now + windowMillis)
                : new Window(w.count() + 1, w.resetAtMillis()));
    }

    @Override
    public void reset(String key) {
        windows.remove(key);
    }
}
