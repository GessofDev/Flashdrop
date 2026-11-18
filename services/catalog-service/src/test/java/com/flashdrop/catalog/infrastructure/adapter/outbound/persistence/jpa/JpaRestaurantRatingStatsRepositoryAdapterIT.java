package com.flashdrop.catalog.infrastructure.adapter.outbound.persistence.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

import com.flashdrop.catalog.domain.model.RatingSummary;

/**
 * Migracion V7 y cache de promedios contra un PostgreSQL real, con
 * ddl-auto=validate.
 */
@DataJpaTest(properties = {
        "spring.config.import=",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("postgres")
@Import(JpaRestaurantRatingStatsRepositoryAdapter.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@Testcontainers(disabledWithoutDocker = true)
class JpaRestaurantRatingStatsRepositoryAdapterIT {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private final JpaRestaurantRatingStatsRepositoryAdapter adapter;
    private final JdbcTemplate jdbc;

    JpaRestaurantRatingStatsRepositoryAdapterIT(JpaRestaurantRatingStatsRepositoryAdapter adapter, JdbcTemplate jdbc) {
        this.adapter = adapter;
        this.jdbc = jdbc;
    }

    @Test
    @DisplayName("la migracion V7 deja la tabla de promedios con la forma esperada")
    void theRatingStatsTableExists() {
        Integer count = jdbc.queryForObject("""
                select count(*) from information_schema.tables
                 where table_schema = 'public' and table_name = 'restaurant_rating_stats'
                """, Integer.class);

        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("el pull deja cacheado el promedio y el conteo del restaurante")
    void replaceBatch_cachesTheAverageAndTheCount() {
        adapter.replaceBatch(List.of(1L), Map.of(1L, new RatingSummary(4.5, 12L)));

        assertThat(adapter.findByRestaurantIds(List.of(1L)))
                .containsEntry(1L, new RatingSummary(4.5, 12L));
    }

    @Test
    @DisplayName("un segundo pull actualiza el valor en vez de duplicar la fila")
    void replaceBatch_updatesTheCachedValue() {
        adapter.replaceBatch(List.of(1L), Map.of(1L, new RatingSummary(4.5, 12L)));
        adapter.replaceBatch(List.of(1L), Map.of(1L, new RatingSummary(2.0, 3L)));

        assertThat(adapter.findByRestaurantIds(List.of(1L)))
                .containsEntry(1L, new RatingSummary(2.0, 3L));
        assertThat(jdbc.queryForObject("select count(*) from restaurant_rating_stats", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("un restaurante que dejo de tener calificaciones queda sin cache")
    void replaceBatch_dropsTheRestaurantsWithoutRatings() {
        adapter.replaceBatch(List.of(1L, 2L), Map.of(
                1L, new RatingSummary(4.5, 12L),
                2L, new RatingSummary(3.0, 4L)));

        adapter.replaceBatch(List.of(1L, 2L), Map.of(1L, new RatingSummary(4.5, 12L)));

        assertThat(adapter.findByRestaurantIds(List.of(2L))).isEmpty();
        assertThat(adapter.findByRestaurantIds(List.of(1L)))
                .containsEntry(1L, new RatingSummary(4.5, 12L));
    }
}
