package com.flashdrop.delivery.infrastructure.adapter.outbound.redis;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashdrop.delivery.application.port.outbound.CourierLocationStore;
import com.flashdrop.delivery.domain.valueobjects.CourierLocation;

@Component
@ConditionalOnProperty(name = "delivery.location.redis.enabled", havingValue = "true")
public class RedisCourierLocationStoreAdapter implements CourierLocationStore {

    private static final Logger log = LoggerFactory.getLogger(RedisCourierLocationStoreAdapter.class);
    private static final String KEY_PREFIX = "courier:";
    private static final String KEY_SUFFIX = ":loc";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public RedisCourierLocationStoreAdapter(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${delivery.location.ttl-seconds:30}") long ttlSeconds
    ) {
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("delivery.location.ttl-seconds must be positive");
        }
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    @Override
    public void save(Long deliveryPersonId, CourierLocation location) {
        if (deliveryPersonId == null) {
            throw new IllegalArgumentException("deliveryPersonId is required");
        }
        if (location == null) {
            throw new IllegalArgumentException("location is required");
        }
        String payload = serialize(new LocationPayload(
                location.latitude(),
                location.longitude(),
                location.recordedAt().toString()
        ));
        redisTemplate.opsForValue().set(key(deliveryPersonId), payload, ttl);
    }

    @Override
    public Optional<CourierLocation> findByDeliveryPersonId(Long deliveryPersonId) {
        if (deliveryPersonId == null) {
            return Optional.empty();
        }
        String raw = redisTemplate.opsForValue().get(key(deliveryPersonId));
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return deserialize(deliveryPersonId, raw);
    }

    @Override
    public void deleteByDeliveryPersonId(Long deliveryPersonId) {
        if (deliveryPersonId == null) {
            return;
        }
        redisTemplate.delete(key(deliveryPersonId));
    }

    static String key(Long deliveryPersonId) {
        return KEY_PREFIX + deliveryPersonId + KEY_SUFFIX;
    }

    private String serialize(LocationPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize courier location", ex);
        }
    }

    private Optional<CourierLocation> deserialize(Long deliveryPersonId, String raw) {
        try {
            LocationPayload payload = objectMapper.readValue(raw, LocationPayload.class);
            if (payload.latitude() == null || payload.longitude() == null || payload.recordedAt() == null) {
                log.warn("Corrupt courier location entry for deliveryPersonId={}", deliveryPersonId);
                return Optional.empty();
            }
            return Optional.of(new CourierLocation(
                    payload.latitude(),
                    payload.longitude(),
                    Instant.parse(payload.recordedAt())
            ));
        } catch (Exception ex) {
            log.warn("Failed to deserialize courier location for deliveryPersonId={}: {}",
                    deliveryPersonId, ex.getMessage());
            return Optional.empty();
        }
    }

    private record LocationPayload(BigDecimal latitude, BigDecimal longitude, String recordedAt) {
    }
}
