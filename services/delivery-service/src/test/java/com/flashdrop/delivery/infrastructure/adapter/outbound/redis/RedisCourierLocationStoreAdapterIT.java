package com.flashdrop.delivery.infrastructure.adapter.outbound.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashdrop.delivery.domain.valueobjects.CourierLocation;

@Testcontainers(disabledWithoutDocker = true)
class RedisCourierLocationStoreAdapterIT {

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    private static StringRedisTemplate redisTemplate;

    @BeforeAll
    static void setUpRedis() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(
                redis.getHost(), redis.getMappedPort(6379));
        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        factory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(factory);
        redisTemplate.afterPropertiesSet();
    }

    @Test
    void saveFindOverwriteDeleteAndTtlExpiration() throws InterruptedException {
        RedisCourierLocationStoreAdapter adapter = new RedisCourierLocationStoreAdapter(
                redisTemplate, new ObjectMapper(), 2L);

        Instant t1 = Instant.parse("2026-11-19T12:00:00Z");
        CourierLocation first = new CourierLocation(new BigDecimal("-33.448900"), new BigDecimal("-70.669300"), t1);
        adapter.save(10L, first);

        Optional<CourierLocation> loaded = adapter.findByDeliveryPersonId(10L);
        assertThat(loaded).isPresent();
        assertThat(loaded.get().latitude()).isEqualByComparingTo("-33.448900");
        assertThat(loaded.get().longitude()).isEqualByComparingTo("-70.669300");
        assertThat(loaded.get().recordedAt()).isEqualTo(t1);

        Instant t2 = Instant.parse("2026-11-19T12:00:05Z");
        CourierLocation second = new CourierLocation(new BigDecimal("-33.450000"), new BigDecimal("-70.670000"), t2);
        adapter.save(10L, second);
        assertThat(adapter.findByDeliveryPersonId(10L)).contains(second);

        adapter.deleteByDeliveryPersonId(10L);
        assertThat(adapter.findByDeliveryPersonId(10L)).isEmpty();

        adapter.save(11L, first);
        assertThat(adapter.findByDeliveryPersonId(11L)).isPresent();
        Thread.sleep(2300L);
        assertThat(adapter.findByDeliveryPersonId(11L)).isEmpty();
    }
}
