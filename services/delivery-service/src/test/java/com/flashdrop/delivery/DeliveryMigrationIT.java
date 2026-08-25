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
@Testcontainers
@DisplayName("DeliveryMigrationIT — KAN-45: BDD-style migration scenario")
class DeliveryMigrationIT {

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
    }

    @Test
    @DisplayName("Scenario 1 — given empty DB when app boots then delivery_persons has seeded U1 row")
    void seededU1RowPresent() {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM internal.delivery_persons WHERE user_id = 'U1'",
                Long.class
        );
        assertThat(count).isGreaterThanOrEqualTo(1L);
    }

    @Test
    @DisplayName("Scenario 10 — given empty DB when app boots then all three tables exist")
    void allThreeTablesExist() {
        assertTableExists("delivery");
        assertTableExists("delivery_routes");
        assertTableExists("delivery_persons");
    }

    private void assertTableExists(String tableName) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'internal' AND table_name = ?",
                Integer.class,
                tableName
        );
        assertThat(count).isEqualTo(1);
    }
}
