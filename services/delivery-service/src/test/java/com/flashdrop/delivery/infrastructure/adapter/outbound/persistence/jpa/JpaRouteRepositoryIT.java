package com.flashdrop.delivery.infrastructure.adapter.outbound.persistence.jpa;

import com.flashdrop.delivery.domain.exception.RouteAlreadyAssignedException;
import com.flashdrop.delivery.domain.exception.RouteReleaseConflictException;
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
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("delivery")
@Testcontainers(disabledWithoutDocker = true)
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
    @DisplayName("F5: anonymizeDeliveryAddresses blanks only the given orders and keeps the pickup address")
    void anonymizeDeliveryAddressesBlanksOnlyTheGivenOrders() {
        DeliveryRouteJpaEntity mine = jpaRouteRepository.save(new DeliveryRouteJpaEntity(
                null, 5001L, "Local A", "Casa de Ana 123", BigDecimal.ONE, 10, "Entregado", Instant.now()));
        DeliveryRouteJpaEntity other = jpaRouteRepository.save(new DeliveryRouteJpaEntity(
                null, 5002L, "Local B", "Casa de Luis 456", BigDecimal.ONE, 10, "Entregado", Instant.now()));

        assertThat(adapter.anonymizeDeliveryAddresses(List.of(5001L))).isEqualTo(1);
        assertThat(adapter.anonymizeDeliveryAddresses(List.of(5001L))).isZero();
        assertThat(adapter.anonymizeDeliveryAddresses(List.of())).isZero();

        assertThat(adapter.findById(mine.getId()).orElseThrow().getDeliveryAddress()).isEmpty();
        assertThat(adapter.findById(mine.getId()).orElseThrow().getPickupAddress()).isEqualTo("Local A");
        assertThat(adapter.findById(other.getId()).orElseThrow().getDeliveryAddress()).isEqualTo("Casa de Luis 456");
    }

    @Test
    @DisplayName("F5: clearDeliveryPerson unlinks every route of that courier and no other")
    void clearDeliveryPersonUnlinksOnlyThatCourier() {
        DeliveryRouteJpaEntity a = new DeliveryRouteJpaEntity(
                null, 5101L, "P", "D", BigDecimal.ONE, 10, "Entregado", Instant.now());
        a.setDeliveryPersonId(901L);
        DeliveryRouteJpaEntity b = new DeliveryRouteJpaEntity(
                null, 5102L, "P", "D", BigDecimal.ONE, 10, "Entregado", Instant.now());
        b.setDeliveryPersonId(902L);
        a = jpaRouteRepository.save(a);
        b = jpaRouteRepository.save(b);

        assertThat(adapter.clearDeliveryPerson(901L)).isEqualTo(1);

        assertThat(jpaRouteRepository.findById(a.getId()).orElseThrow().getDeliveryPersonId()).isNull();
        assertThat(jpaRouteRepository.findById(b.getId()).orElseThrow().getDeliveryPersonId()).isEqualTo(902L);
    }

    @Test
    @DisplayName("releaseDeliveryPerson runs in its own transaction — no ambient transaction required")
    void releaseDeliveryPerson_worksWithoutAmbientTransaction() {
        DeliveryRouteJpaEntity entity = new DeliveryRouteJpaEntity(
                null, 5201L, 42L, "Pickup", "Delivery", BigDecimal.ONE, 10, "Asignado", Instant.now());
        entity = jpaRouteRepository.save(entity);

        DeliveryRoute released = adapter.releaseDeliveryPerson(5201L, 42L);

        assertThat(released.getDeliveryPersonId()).isNull();
        assertThat(released.getStatus()).isEqualTo(RouteStatus.PENDIENTE);
        assertThat(jpaRouteRepository.findById(entity.getId()).orElseThrow().getDeliveryPersonId()).isNull();
        assertThat(jpaRouteRepository.findById(entity.getId()).orElseThrow().getStatus())
                .isEqualTo(RouteStatus.PENDIENTE.getDbValue());
    }

    @Test
    @DisplayName("closeRoute runs in its own transaction — no ambient transaction required")
    void closeRoute_worksWithoutAmbientTransaction() {
        DeliveryRouteJpaEntity entity = new DeliveryRouteJpaEntity(
                null, 5202L, 42L, "Pickup", "Delivery", BigDecimal.ONE, 10, "En camino", Instant.now());
        entity = jpaRouteRepository.save(entity);

        DeliveryRoute closed = adapter.closeRoute(entity.getId(), RouteStatus.CANCELADO);

        assertThat(closed.getStatus()).isEqualTo(RouteStatus.CANCELADO);
        assertThat(jpaRouteRepository.findById(entity.getId()).orElseThrow().getStatus())
                .isEqualTo(RouteStatus.CANCELADO.getDbValue());
    }

    @Test
    @DisplayName("concurrent closes of the same route: the row lock lets exactly one win")
    void concurrentClose_onlyOneWins() throws Exception {
        DeliveryRouteJpaEntity entity = new DeliveryRouteJpaEntity(
                null, 5203L, 42L, "Pickup", "Delivery", BigDecimal.ONE, 10, "En camino", Instant.now());
        entity = jpaRouteRepository.save(entity);
        Long routeId = entity.getId();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Throwable> cancel = pool.submit(() -> closeRoute(start, routeId, RouteStatus.CANCELADO));
            Future<Throwable> reject = pool.submit(() -> closeRoute(start, routeId, RouteStatus.RECHAZADO));
            start.countDown();

            Throwable cancelFailure = cancel.get(30, TimeUnit.SECONDS);
            Throwable rejectFailure = reject.get(30, TimeUnit.SECONDS);

            // Exactly one close commits; the loser observes the terminal state
            // written by the winner and is rejected instead of overwriting it.
            assertThat(java.util.Arrays.asList(cancelFailure, rejectFailure))
                    .filteredOn(Objects::nonNull)
                    .hasSize(1)
                    .allSatisfy(failure -> assertThat(failure)
                            .isInstanceOf(RouteReleaseConflictException.class));

            String persistedStatus = jpaRouteRepository.findById(routeId).orElseThrow().getStatus();
            assertThat(persistedStatus)
                    .isIn(RouteStatus.CANCELADO.getDbValue(), RouteStatus.RECHAZADO.getDbValue());
        } finally {
            pool.shutdownNow();
        }
    }

    private Throwable closeRoute(CountDownLatch start, Long routeId, RouteStatus targetStatus) {
        try {
            start.await(10, TimeUnit.SECONDS);
            adapter.closeRoute(routeId, targetStatus);
            return null;
        } catch (Throwable failure) {
            return failure;
        }
    }

    @Test
    @DisplayName("assignDeliveryPerson runs in its own transaction — no ambient transaction required")
    void assignDeliveryPerson_worksWithoutAmbientTransaction() {
        DeliveryRouteJpaEntity entity = new DeliveryRouteJpaEntity(
                null, 5204L, null, "Pickup", "Delivery", BigDecimal.ONE, 10, "Pendiente", Instant.now());
        entity = jpaRouteRepository.save(entity);

        DeliveryRoute claimed = adapter.assignDeliveryPerson(5204L, 42L);

        assertThat(claimed.getDeliveryPersonId()).isEqualTo(42L);
        assertThat(claimed.getStatus()).isEqualTo(RouteStatus.ASSIGNED);
        assertThat(jpaRouteRepository.findById(entity.getId()).orElseThrow().getDeliveryPersonId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("concurrent claims of the same route: the row lock lets exactly one win")
    void concurrentClaim_onlyOneWins() throws Exception {
        DeliveryRouteJpaEntity entity = new DeliveryRouteJpaEntity(
                null, 5205L, null, "Pickup", "Delivery", BigDecimal.ONE, 10, "Pendiente", Instant.now());
        entity = jpaRouteRepository.save(entity);
        Long orderId = 5205L;

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Throwable> first = pool.submit(() -> claimRoute(start, orderId, 42L));
            Future<Throwable> second = pool.submit(() -> claimRoute(start, orderId, 77L));
            start.countDown();

            Throwable firstFailure = first.get(30, TimeUnit.SECONDS);
            Throwable secondFailure = second.get(30, TimeUnit.SECONDS);

            // Exactly one claim commits; the loser observes the assignment
            // written by the winner and is rejected instead of overwriting it.
            assertThat(java.util.Arrays.asList(firstFailure, secondFailure))
                    .filteredOn(Objects::nonNull)
                    .hasSize(1)
                    .allSatisfy(failure -> assertThat(failure)
                            .isInstanceOf(RouteAlreadyAssignedException.class));

            Long assignedCourier = jpaRouteRepository.findByOrderId(orderId).orElseThrow().getDeliveryPersonId();
            assertThat(assignedCourier).isIn(42L, 77L);
        } finally {
            pool.shutdownNow();
        }
    }

    private Throwable claimRoute(CountDownLatch start, Long orderId, Long deliveryPersonId) {
        try {
            start.await(10, TimeUnit.SECONDS);
            adapter.assignDeliveryPerson(orderId, deliveryPersonId);
            return null;
        } catch (Throwable failure) {
            return failure;
        }
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
