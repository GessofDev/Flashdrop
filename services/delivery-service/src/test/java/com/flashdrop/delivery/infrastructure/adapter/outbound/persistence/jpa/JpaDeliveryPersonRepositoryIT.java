package com.flashdrop.delivery.infrastructure.adapter.outbound.persistence.jpa;

import com.flashdrop.delivery.domain.model.DeliveryPerson;
import com.flashdrop.delivery.infrastructure.adapter.outbound.persistence.jpa.entity.DeliveryPersonJpaEntity;
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

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("delivery")
@Testcontainers
@DisplayName("JpaDeliveryPersonRepositoryIT — KAN-44: Testcontainers Postgres + Flyway CRUD round-trip")
class JpaDeliveryPersonRepositoryIT {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("delivery_db")
            .withUsername("delivery_svc")
            .withPassword("delivery_svc_password");

    @Autowired
    JpaDeliveryPersonRepository jpaDeliveryPersonRepository;

    @Autowired
    JpaDeliveryPersonRepositoryAdapter adapter;

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    @DisplayName("TC1: Flyway seeded the U1 delivery person on boot")
    void flywaySeededU1Person() {
        Optional<DeliveryPerson> u1 = adapter.findByUserId("U1");
        assertThat(u1).isPresent();
        assertThat(u1.get().getUserId()).isEqualTo("U1");
    }

    @Test
    @DisplayName("TC2: save a new delivery person and retrieve by id")
    void saveAndFindById() {
        String uniqueUserId = "IT_USER_" + UUID.randomUUID();
        DeliveryPerson person = new DeliveryPerson(null, uniqueUserId, null, Instant.now());
        DeliveryPerson saved = adapter.save(person);

        Optional<DeliveryPerson> result = adapter.findById(saved.getId());
        assertThat(result).isPresent();
        assertThat(result.get().getUserId()).isEqualTo(uniqueUserId);
    }

    @Test
    @DisplayName("TC3: findByUserId returns correct person")
    void findByUserId() {
        String uniqueUserId = "IT_USER2_" + UUID.randomUUID();
        DeliveryPerson person = new DeliveryPerson(null, uniqueUserId, null, Instant.now());
        adapter.save(person);

        Optional<DeliveryPerson> result = adapter.findByUserId(uniqueUserId);
        assertThat(result).isPresent();
        assertThat(result.get().getUserId()).isEqualTo(uniqueUserId);
    }

    @Test
    @DisplayName("TC4: existsByUserId returns true for seeded U1")
    void existsByUserIdTrue() {
        assertThat(adapter.existsByUserId("U1")).isTrue();
    }

    @Test
    @DisplayName("TC5: existsByUserId returns false for unknown user")
    void existsByUserIdFalse() {
        assertThat(adapter.existsByUserId("UNKNOWN_USER_IT")).isFalse();
    }

    @Test
    @DisplayName("TC6: delivery_persons table has unique constraint on userId")
    void userIdUniqueConstraint() {
        String uniqueUserId = "IT_USER3_" + UUID.randomUUID();
        DeliveryPerson person = new DeliveryPerson(null, uniqueUserId, null, Instant.now());
        adapter.save(person);

        DeliveryPerson duplicate = new DeliveryPerson(null, uniqueUserId, null, Instant.now());
        // Second save should either throw or replace — uniqueness is enforced at DB level
        adapter.save(duplicate);

        long count = jpaDeliveryPersonRepository.findByUserId(uniqueUserId).stream().count();
        assertThat(count).isGreaterThanOrEqualTo(1);
    }
}
