package com.flashdrop.catalog.infrastructure.adapter.outbound.persistence.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.flashdrop.catalog.domain.model.OpeningHours;
import com.flashdrop.catalog.domain.model.Restaurant;

/** Migracion V5 y mapeo del horario contra un PostgreSQL real, con ddl-auto=validate. */
@DataJpaTest(properties = {
        "spring.config.import=",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("postgres")
@Import(JpaRestaurantRepositoryAdapter.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@Testcontainers(disabledWithoutDocker = true)
class JpaRestaurantRepositoryAdapterIT {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private static final OpeningHours LUNES =
            new OpeningHours(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(18, 0));
    private static final OpeningHours VIERNES =
            new OpeningHours(DayOfWeek.FRIDAY, LocalTime.of(22, 0), LocalTime.of(2, 0));

    private final JpaRestaurantRepositoryAdapter repository;
    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;

    JpaRestaurantRepositoryAdapterIT(JpaRestaurantRepositoryAdapter repository,
                                     JdbcTemplate jdbc, EntityManager entityManager) {
        this.repository = repository;
        this.jdbc = jdbc;
        this.entityManager = entityManager;
    }

    @BeforeEach
    void insertRestaurant() {
        jdbc.update("insert into restaurant (id, user_id, name, address) values (1, 10, 'Local', 'Calle')");
    }

    private Restaurant reload() {
        entityManager.flush();
        entityManager.clear();
        return repository.findById(1L).orElseThrow();
    }

    @Test
    void existingRestaurantsKeepAcceptingOrdersWithoutHours() {
        Restaurant restaurant = reload();

        assertThat(restaurant.isAcceptingOrders()).isTrue();
        assertThat(restaurant.getHours()).isEmpty();
    }

    @Test
    void hoursAreStoredAndReplacedWithoutColliding() {
        repository.replaceHours(1L, List.of(LUNES, VIERNES));
        assertThat(reload().getHours()).containsExactlyInAnyOrder(LUNES, VIERNES);

        repository.replaceHours(1L, List.of(LUNES));
        assertThat(reload().getHours()).containsExactly(LUNES);

        repository.replaceHours(1L, List.of());
        assertThat(reload().getHours()).isEmpty();
    }

    @Test
    void theSwitchIsPersisted() {
        repository.updateAcceptingOrders(1L, false);

        assertThat(reload().isAcceptingOrders()).isFalse();
    }

    @Test
    void listingBringsTheHoursOfEveryRestaurant() {
        repository.replaceHours(1L, List.of(LUNES));
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findAll()).singleElement()
                .satisfies(restaurant -> assertThat(restaurant.getHours()).containsExactly(LUNES));
    }
}
