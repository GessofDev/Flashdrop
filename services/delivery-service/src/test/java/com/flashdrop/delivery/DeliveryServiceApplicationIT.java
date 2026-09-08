package com.flashdrop.delivery;

import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("delivery")
@Testcontainers
@DisplayName("DeliveryServiceApplicationIT — KAN-45: Spring Boot full-context with Testcontainers")
class DeliveryServiceApplicationIT {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("delivery_db")
            .withUsername("delivery_svc")
            .withPassword("delivery_svc_password");

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // PR-A: ObservabilityAutoConfiguration fails loud if these are missing
        // in non-dev profiles. The IT does not exercise auth — provide dummy
        // values so the context can boot. JWKS preload is allowed to fail
        // (caught + logged inside JwksKeyProvider).
        registry.add("internal.api.key", () -> "test-internal-key");
        registry.add("auth.jwks-uri", () -> "http://localhost:1/jwks.json");
        registry.add("auth.issuer", () -> "flashdrop-auth");
    }

    @Test
    @DisplayName("TC1: Spring context boots successfully with delivery profile")
    void contextLoads() {
        // If this test passes, the full Spring context was built without errors
        assertThat(true).isTrue();
    }

    @Test
    @DisplayName("TC2: Flyway migrations ran — V1 and V2 applied")
    void flywayMigrationsApplied() {
        // The delivery_routes table must exist and be accessible
        int count = jdbcTemplate().queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'internal' AND table_name = 'delivery_routes'",
                Integer.class
        );
        assertThat(count).isEqualTo(1);
    }

    private static org.springframework.jdbc.core.JdbcTemplate jdbcTemplate() {
        return new org.springframework.jdbc.core.JdbcTemplate(
                new org.springframework.jdbc.datasource.DriverManagerDataSource(
                        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()
                )
        );
    }
}
