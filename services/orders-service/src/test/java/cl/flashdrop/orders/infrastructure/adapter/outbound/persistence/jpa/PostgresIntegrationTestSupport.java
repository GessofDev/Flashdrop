package cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base compartida para los tests de integración JPA reales (GAP-02, auditoría 2026-09-04).
 *
 * <p>Levanta UN PostgreSQL real vía Testcontainers (patrón "singleton container": el
 * contenedor se arranca una sola vez en el {@code static} initializer y se comparte entre
 * todas las subclases de esta JVM de test — Testcontainers/Ryuk lo detiene al terminar la
 * suite, no hace falta pararlo manualmente) y deja que {@code @DataJpaTest} ejecute contra
 * él las migraciones Flyway REALES ({@code db/migration/V1__init.sql}) — el mismo esquema
 * que corre en producción, no un H2 en memoria con un dialecto distinto.</p>
 *
 * <p>Antes de esta clase, {@code JpaOrderRepositoryAdapter}/{@code JpaClientAdapter} — los
 * adapters que realmente usa el perfil {@code postgres}/{@code default} en producción —
 * no tenían ninguna cobertura automatizada (ver informe de auditoría, GAP-02). El único
 * test "E2E" existente ({@code OrdersE2ESimulatedTest}) ejercita explícitamente el camino
 * legacy Supabase, no este.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
abstract class PostgresIntegrationTestSupport {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
                    .withDatabaseName("flashdrop_orders_test")
                    .withUsername("orders_app")
                    .withPassword("orders_app_pass");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // application.properties ya trae spring.flyway.enabled=true y
        // spring.jpa.hibernate.ddl-auto=none por defecto (perfil postgres): el esquema lo
        // crea únicamente V1__init.sql, igual que en producción.
    }
}
