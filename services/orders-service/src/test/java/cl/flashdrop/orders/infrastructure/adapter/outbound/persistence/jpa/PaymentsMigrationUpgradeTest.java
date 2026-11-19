package cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * El escenario del servidor (FlashDrop 1.0 tiendas, spec 4.1): una base que YA tiene pedidos
 * reales creados con V1 a V5 recibe V6 a V9. Los pedidos antiguos deben seguir intactos y
 * legibles, sin pagos, sin llave y sin plazos. Es la prueba que reemplaza, en CI, al ensayo
 * sobre una copia de la base real.
 */
class PaymentsMigrationUpgradeTest {

    @Test
    void ordersCreatedBeforeThePaymentMigrationsSurviveThem() throws Exception {
        try (PostgreSQLContainer<?> db = new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
                .withDatabaseName("orders_upgrade").withUsername("orders_app").withPassword("orders_app_pass")) {
            db.start();

            // 1) El servidor tal como esta hoy: solo V1 a V5.
            flyway(db, "5").migrate();
            try (Connection c = DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
                 Statement s = c.createStatement()) {
                s.execute("INSERT INTO client(user_id) VALUES (1001)");
                s.execute("INSERT INTO orders(client_id, restaurant_id, status, address, subtotal, delivery_fee, total, payment_method) "
                        + "VALUES (1, 7, 'Entregado', 'Av. Antigua 1', 7500, 2500, 10000, 'Efectivo')");
                s.execute("INSERT INTO orders(client_id, restaurant_id, status, address, subtotal, delivery_fee, total, payment_method) "
                        + "VALUES (1, 7, 'Nuevo pedido', 'Av. Antigua 2', 5000, 2500, 7500, 'Tarjeta')");
            }

            // 2) Se aplican las migraciones nuevas, como hace el deploy.
            var result = flyway(db, null).migrate();
            assertTrue(result.migrationsExecuted >= 4, "Al menos V6, V7, V8 y V9");

            try (Connection c = DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
                 Statement s = c.createStatement()) {
                // 3) Los pedidos viejos siguen ahi, intactos y con los campos nuevos vacios.
                ResultSet rs = s.executeQuery("SELECT status, total, idempotency_key, payment_expires_at, "
                        + "store_response_due_at FROM orders ORDER BY id");
                assertTrue(rs.next());
                assertEquals("Entregado", rs.getString("status"));
                assertEquals(0, rs.getBigDecimal("total").compareTo(java.math.BigDecimal.valueOf(10000)));
                assertNull(rs.getString("idempotency_key"));
                assertNull(rs.getTimestamp("payment_expires_at"));
                assertNull(rs.getTimestamp("store_response_due_at"));
                assertTrue(rs.next());
                assertEquals("Nuevo pedido", rs.getString("status"));
                assertFalse(rs.next());

                // 4) No se inventan pagos ni reembolsos retroactivos.
                assertEquals(0, count(s, "payments"));
                assertEquals(0, count(s, "refunds"));
                // 5) La politica global quedo sembrada.
                assertEquals(1, count(s, "order_policies"));

                // 6) Los pedidos viejos aceptan un pago nuevo (las tablas quedaron enlazadas).
                s.execute("INSERT INTO payments(order_id, provider, provider_tx_id, amount, status) "
                        + "VALUES (2, 'fake', 'tx-legacy', 7500, 'PENDIENTE')");
                assertEquals(1, count(s, "payments"));
            }

            // 7) Una segunda ejecucion no hace nada (el deploy puede repetirse).
            assertEquals(0, flyway(db, null).migrate().migrationsExecuted);
        }
    }

    private static Flyway flyway(PostgreSQLContainer<?> db, String target) {
        var cfg = Flyway.configure()
                .dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
                .locations("classpath:db/migration");
        if (target != null) {
            cfg.target(target);
        }
        return cfg.load();
    }

    private static int count(Statement s, String table) throws Exception {
        try (ResultSet rs = s.executeQuery("SELECT count(*) FROM " + table)) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
