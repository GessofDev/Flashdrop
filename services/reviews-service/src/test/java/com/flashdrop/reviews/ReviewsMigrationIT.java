package com.flashdrop.reviews;

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

/**
 * La migración V1 sobre una base nueva deja el esquema esperado (T3).
 */
@SpringBootTest
@ActiveProfiles("reviews")
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("ReviewsMigrationIT — el esquema que deja V1__reviews.sql")
class ReviewsMigrationIT {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("reviews_db")
            .withUsername("reviews_svc")
            .withPassword("reviews_svc_password");

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
    @DisplayName("TC1: la tabla internal.reviews existe")
    void reviewsTableExists() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = 'internal' AND table_name = 'reviews'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("TC2: las columnas esperadas existen con el tipo correcto")
    void columnsMatchTheEntity() {
        String sql = "SELECT column_name, data_type, is_nullable FROM information_schema.columns "
                + "WHERE table_schema = 'internal' AND table_name = 'reviews'";
        var rows = jdbc.queryForList(sql);

        assertThat(rows).extracting(row -> row.get("column_name"))
                .contains("id", "order_id", "author_user_id", "target_type", "target_id",
                        "rating", "comment", "status", "created_at", "updated_at");

        assertThat(rows).filteredOn(row -> "rating".equals(row.get("column_name")))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.get("data_type")).isEqualTo("smallint");
                    assertThat(row.get("is_nullable")).isEqualTo("NO");
                });

        assertThat(rows).filteredOn(row -> "comment".equals(row.get("column_name")))
                .singleElement()
                .satisfies(row -> assertThat(row.get("is_nullable")).isEqualTo("YES"));

        assertThat(rows).filteredOn(row -> "target_type".equals(row.get("column_name")))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.get("data_type")).isEqualTo("character varying");
                    assertThat(row.get("is_nullable")).isEqualTo("NO");
                });
    }

    @Test
    @DisplayName("TC3: existe la restricción de unicidad uq_review_participant")
    void uniqueConstraintExists() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints "
                        + "WHERE table_schema = 'internal' AND table_name = 'reviews' "
                        + "AND constraint_name = 'uq_review_participant'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("TC4: existen los índices por destino y por autor")
    void indexesExist() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes "
                        + "WHERE schemaname = 'internal' AND tablename = 'reviews' "
                        + "AND indexname IN ('idx_reviews_target', 'idx_reviews_author')",
                Integer.class);
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("TC5: el estado por defecto de una calificación es PUBLICADO")
    void defaultStatusIsPublished() {
        jdbc.update("INSERT INTO internal.reviews "
                        + "(order_id, author_user_id, target_type, target_id, rating) "
                        + "VALUES (?, ?, ?, ?, ?)",
                1L, 2L, "TIENDA", 3L, (short) 4);

        String status = jdbc.queryForObject(
                "SELECT status FROM internal.reviews WHERE order_id = 1", String.class);
        assertThat(status).isEqualTo("PUBLICADO");
    }
}
