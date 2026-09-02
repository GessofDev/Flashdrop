package com.flashdrop.delivery.infrastructure.adapter.outbound.persistence.jpa;

import com.flashdrop.delivery.domain.model.DeliveryRoute;
import com.flashdrop.delivery.domain.valueobjects.Distance;
import com.flashdrop.delivery.domain.valueobjects.EstimatedTime;
import com.flashdrop.delivery.domain.valueobjects.RouteStatus;
import com.flashdrop.delivery.infrastructure.adapter.outbound.persistence.jpa.entity.DeliveryRouteJpaEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("delivery")
@Testcontainers
@DisplayName("JpaRouteRepositoryIT — KAN-44: Testcontainers Postgres + Flyway CRUD round-trip")
class JpaRouteRepositoryIT {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("delivery_db")
            .withUsername("delivery_svc")
            .withPassword("delivery_svc_password");

    @Autowired
    JpaDeliveryRouteRepository jpaRouteRepository;

    @Autowired
    JpaRouteRepositoryAdapter adapter;

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // PR-A: fail-loud bean registrations need these in non-dev profiles.
        registry.add("internal.api.key", () -> "test-internal-key");
        registry.add("auth.jwks-uri", () -> "http://localhost:1/jwks.json");
        registry.add("auth.issuer", () -> "flashdrop-auth");
    }

    @Test
    @DisplayName("TC1: Flyway ran on boot — internal schema tables exist")
    void flywayCreatedTablesOnBoot() {
        List<DeliveryRouteJpaEntity> all = jpaRouteRepository.findAll();
        assertThat(all).isNotNull();
    }

    @Test
    @DisplayName("TC2: save a route and retrieve it by id")
    void saveAndFindById() {
        DeliveryRouteJpaEntity entity = new DeliveryRouteJpaEntity(
                null, 1001L, "Pickup A", "Delivery B",
                BigDecimal.valueOf(3.5), 20,
                "Pendiente", Instant.now()
        );
        DeliveryRouteJpaEntity saved = jpaRouteRepository.save(entity);

        Optional<DeliveryRoute> result = adapter.findById(saved.getId());
        assertThat(result).isPresent();
        assertThat(result.get().getOrderId()).isEqualTo(1001L);
        assertThat(result.get().getPickupAddress()).isEqualTo("Pickup A");
        assertThat(result.get().getDeliveryAddress()).isEqualTo("Delivery B");
        assertThat(result.get().getDistanceKm().value()).isEqualByComparingTo(BigDecimal.valueOf(3.5));
        assertThat(result.get().getEstimatedMinutes().minutes()).isEqualTo(20);
        assertThat(result.get().getStatus()).isEqualTo(RouteStatus.PENDIENTE);
    }

    @Test
    @DisplayName("TC3: findByOrderId returns the correct route")
    void findByOrderId() {
        DeliveryRouteJpaEntity entity = new DeliveryRouteJpaEntity(
                null, 1002L, "Pickup C", "Delivery D",
                BigDecimal.valueOf(5.0), 30,
                "Asignado", Instant.now()
        );
        jpaRouteRepository.save(entity);

        Optional<DeliveryRoute> result = adapter.findByOrderId(1002L);
        assertThat(result).isPresent();
        assertThat(result.get().getOrderId()).isEqualTo(1002L);
        assertThat(result.get().getStatus()).isEqualTo(RouteStatus.ASSIGNED);
    }

    @Test
    @DisplayName("TC4: findAll returns all saved routes")
    void findAll() {
        long countBefore = adapter.findAll().size();
        jpaRouteRepository.save(new DeliveryRouteJpaEntity(
                null, 1003L, "Pickup E", "Delivery F",
                BigDecimal.valueOf(1.0), 5,
                "Pendiente", Instant.now()
        ));

        List<DeliveryRoute> all = adapter.findAll();
        assertThat(all).hasSize((int) countBefore + 1);
    }

    @Test
    @DisplayName("TC5: updateStatus changes route status")
    void updateStatus() {
        DeliveryRouteJpaEntity entity = new DeliveryRouteJpaEntity(
                null, 1004L, "Pickup G", "Delivery H",
                BigDecimal.valueOf(2.0), 10,
                "Pendiente", Instant.now()
        );
        DeliveryRouteJpaEntity saved = jpaRouteRepository.save(entity);

        DeliveryRoute updated = adapter.updateStatus(saved.getId(), "Entregado");

        assertThat(updated).isNotNull();
        assertThat(updated.getStatus()).isEqualTo(RouteStatus.ENTREGADO);
    }

    @Test
    @DisplayName("TC6: existsByOrderId returns true for existing order")
    void existsByOrderIdTrue() {
        DeliveryRouteJpaEntity entity = new DeliveryRouteJpaEntity(
                null, 1005L, "Pickup I", "Delivery J",
                BigDecimal.valueOf(4.0), 15,
                "Pendiente", Instant.now()
        );
        jpaRouteRepository.save(entity);

        assertThat(adapter.existsByOrderId(1005L)).isTrue();
    }

    @Test
    @DisplayName("TC7: existsByOrderId returns false for non-existing order")
    void existsByOrderIdFalse() {
        assertThat(adapter.existsByOrderId(9999L)).isFalse();
    }
}
