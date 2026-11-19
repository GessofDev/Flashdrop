package com.flashdrop.delivery.infrastructure.adapter.outbound.redis;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import com.flashdrop.delivery.application.port.outbound.CourierLocationStore;
import com.flashdrop.delivery.domain.valueobjects.CourierLocation;

/** Fallback store used when Redis is disabled (e.g. unit/slice tests). */
@Component
@ConditionalOnMissingBean(CourierLocationStore.class)
public class InMemoryCourierLocationStoreAdapter implements CourierLocationStore {

    private final ConcurrentHashMap<Long, Entry> entries = new ConcurrentHashMap<>();
    private final Duration ttl;
    private final Clock clock;

    @Autowired
    public InMemoryCourierLocationStoreAdapter(
            @Value("${delivery.location.ttl-seconds:30}") long ttlSeconds
    ) {
        this(ttlSeconds, Clock.systemUTC());
    }

    InMemoryCourierLocationStoreAdapter(long ttlSeconds, Clock clock) {
        this.ttl = Duration.ofSeconds(ttlSeconds);
        this.clock = clock;
    }

    @Override
    public void save(Long deliveryPersonId, CourierLocation location) {
        if (deliveryPersonId == null || location == null) {
            throw new IllegalArgumentException("deliveryPersonId and location are required");
        }
        entries.put(deliveryPersonId, new Entry(location, Instant.now(clock).plus(ttl)));
    }

    @Override
    public Optional<CourierLocation> findByDeliveryPersonId(Long deliveryPersonId) {
        if (deliveryPersonId == null) {
            return Optional.empty();
        }
        Entry entry = entries.get(deliveryPersonId);
        if (entry == null) {
            return Optional.empty();
        }
        if (Instant.now(clock).isAfter(entry.expiresAt())) {
            entries.remove(deliveryPersonId, entry);
            return Optional.empty();
        }
        return Optional.of(entry.location());
    }

    @Override
    public void deleteByDeliveryPersonId(Long deliveryPersonId) {
        if (deliveryPersonId != null) {
            entries.remove(deliveryPersonId);
        }
    }

    private record Entry(CourierLocation location, Instant expiresAt) {
    }
}
