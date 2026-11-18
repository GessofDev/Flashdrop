package com.flashdrop.delivery;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("delivery")
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("DeliveryPersonRatingStatsMigrationIT — V8: cache de promedios de calificacion")
class DeliveryPersonRatingStatsMigrationIT {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("delivery_db")
            .withUsername("delivery_svc")
            .withPassword("delivery_svc_password");

    @Autowired
    JdbcTemplate jdbc;

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("internal.api.key", () -> "test-internal-key");
        registry.add("auth.jwks-uri", () -> "http://localhost:1/jwks.json");
        registry.add("auth.issuer", () -> "flashdrop-auth");
    }

    @Test
    @DisplayName("la migracion V8 deja la tabla de promedios con la forma esperada")
    void theRatingStatsTableExists() {
        Integer count = jdbc.queryForObject("""
                select count(*) from information_schema.tables
                 where table_schema = 'internal' and table_name = 'delivery_person_rating_stats'
                """, Integer.class);

        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("la cache guarda el promedio y el conteo del repartidor")
    void theTableStoresTheAverageAndTheCount() {
        jdbc.update("""
                insert into internal.delivery_person_rating_stats (courier_user_id, average, count, updated_at)
                values (33, 4.5, 12, now())
                on conflict (courier_user_id) do update
                   set average = excluded.average, count = excluded.count, updated_at = excluded.updated_at
                """);

        Double average = jdbc.queryForObject(
                "select average from internal.delivery_person_rating_stats where courier_user_id = 33",
                Double.class);
        Long count = jdbc.queryForObject(
                "select count from internal.delivery_person_rating_stats where courier_user_id = 33",
                Long.class);

        assertThat(average).isEqualTo(4.5);
        assertThat(count).isEqualTo(12L);
    }
}
