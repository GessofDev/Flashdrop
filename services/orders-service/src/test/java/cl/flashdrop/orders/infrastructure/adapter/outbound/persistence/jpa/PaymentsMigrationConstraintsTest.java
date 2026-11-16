package cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Migraciones V6 a V9 (FlashDrop 1.0 tiendas, spec 4.1) contra PostgreSQL real: lo que debe
 * ser imposible lo impide la BASE DE DATOS, no solo el codigo. Cada caso que debe fallar es el
 * ULTIMO paso del test: en PostgreSQL un error aborta la transaccion en curso.
 */
class PaymentsMigrationConstraintsTest extends PostgresIntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    private long clientId;
    private long orderId;

    @BeforeEach
    void setUp() {
        clientId = insertClient();
        orderId = insertOrder(clientId, null);
    }

    // ----------------------------- helpers -----------------------------

    private long insertClient() {
        return jdbc.queryForObject(
                "INSERT INTO client(user_id) VALUES (?) RETURNING id", Long.class, System.nanoTime());
    }

    private long insertOrder(long client, String idempotencyKey) {
        return jdbc.queryForObject(
                "INSERT INTO orders(client_id, restaurant_id, address, idempotency_key) VALUES (?, 7, 'Calle 1', ?) RETURNING id",
                Long.class, client, idempotencyKey);
    }

    private long insertPayment(long order, String provider, String tx, String status) {
        return jdbc.queryForObject(
                "INSERT INTO payments(order_id, provider, provider_tx_id, amount, status) VALUES (?, ?, ?, 10000, ?) RETURNING id",
                Long.class, order, provider, tx, status);
    }

    private void insertRefund(long payment, long order, String type, String amount, String reason, String status, String key) {
        jdbc.update("INSERT INTO refunds(payment_id, order_id, refund_type, amount, reason, status, refund_key) "
                + "VALUES (?, ?, ?, ?::numeric, ?, ?, ?)", payment, order, type, amount, reason, status, key);
    }

    private void assertRejected(Runnable statement) {
        assertThrows(DataIntegrityViolationException.class, statement::run);
    }

    // ------------------------------ V6: payments ------------------------------

    @Test
    void anOrderCanHaveAFailedAttemptAndThenANewOpenOne() {
        insertPayment(orderId, "fake", "tx-1", "RECHAZADO");
        insertPayment(orderId, "fake", "tx-2", "PENDIENTE");
        insertPayment(orderId, "fake", "tx-3", "EXPIRADO");

        assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM payments WHERE order_id = ?", Integer.class, orderId));
    }

    @Test
    void anOrderCannotHaveTwoApprovedPayments() {
        insertPayment(orderId, "fake", "tx-1", "APROBADO");
        assertRejected(() -> insertPayment(orderId, "fake", "tx-2", "APROBADO"));
    }

    @Test
    void anOrderCannotHaveTwoOpenPayments() {
        insertPayment(orderId, "fake", "tx-1", "PENDIENTE");
        assertRejected(() -> insertPayment(orderId, "fake", "tx-2", "PENDIENTE"));
    }

    @Test
    void theSameProviderTransactionCannotBeRegisteredTwiceButAnotherProviderMay() {
        long otherOrder = insertOrder(clientId, null);
        insertPayment(orderId, "webpay", "tx-shared", "PENDIENTE");
        insertPayment(otherOrder, "flow", "tx-shared", "PENDIENTE");
        long thirdOrder = insertOrder(clientId, null);
        assertRejected(() -> insertPayment(thirdOrder, "webpay", "tx-shared", "PENDIENTE"));
    }

    @Test
    void paymentsRejectUnknownStatusAndNonPositiveAmounts() {
        assertRejected(() -> insertPayment(orderId, "fake", "tx-1", "COBRADO"));
    }

    @Test
    void paymentsRejectZeroAmount() {
        assertRejected(() -> jdbc.update(
                "INSERT INTO payments(order_id, provider, provider_tx_id, amount) VALUES (?, 'fake', 'tx-0', 0)", orderId));
    }

    @Test
    void paymentsRejectAnOrderThatDoesNotExist() {
        assertRejected(() -> insertPayment(987_654_321L, "fake", "tx-orphan", "PENDIENTE"));
    }

    @Test
    void anOrderWithPaymentsCannotBeDeleted() {
        insertPayment(orderId, "fake", "tx-1", "APROBADO");
        assertRejected(() -> jdbc.update("DELETE FROM orders WHERE id = ?", orderId));
    }

    // ------------------------------ V7: orders ------------------------------

    @Test
    void existingStyleOrdersKeepWorkingWithoutThePaymentFields() {
        assertNull(jdbc.queryForObject("SELECT idempotency_key FROM orders WHERE id = ?", String.class, orderId));
        assertNull(jdbc.queryForObject("SELECT payment_expires_at FROM orders WHERE id = ?", java.sql.Timestamp.class, orderId));
        assertNull(jdbc.queryForObject("SELECT store_response_due_at FROM orders WHERE id = ?", java.sql.Timestamp.class, orderId));
    }

    @Test
    void manyOrdersWithoutKeyCoexistAndTheSameKeyIsAllowedForDifferentClients() {
        insertOrder(clientId, null);
        insertOrder(clientId, null);
        long otherClient = insertClient();
        insertOrder(clientId, "same-key");
        insertOrder(otherClient, "same-key");

        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM orders WHERE idempotency_key = 'same-key'", Integer.class));
    }

    @Test
    void theSameClientCannotReuseAnIdempotencyKey() {
        insertOrder(clientId, "dup-key");
        assertRejected(() -> insertOrder(clientId, "dup-key"));
    }

    // ------------------------------ V8: refunds ------------------------------

    @Test
    void aPaymentCanHaveSeveralPartialRefundsButOnlyOneTotal() {
        long payment = insertPayment(orderId, "fake", "tx-1", "APROBADO");
        insertRefund(payment, orderId, "PARCIAL", "2500", "CLIENTE_AUSENTE", "HECHO", "k1");
        insertRefund(payment, orderId, "PARCIAL", "1000", "CANCELACION", "PENDIENTE", "k2");
        insertRefund(payment, orderId, "TOTAL", "10000", "RECHAZO", "PENDIENTE", "k3");

        assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM refunds WHERE payment_id = ?", Integer.class, payment));
        assertRejected(() -> insertRefund(payment, orderId, "TOTAL", "10000", "RECHAZO", "PENDIENTE", "k4"));
    }

    @Test
    void refundKeyIsUnique() {
        long payment = insertPayment(orderId, "fake", "tx-1", "APROBADO");
        insertRefund(payment, orderId, "PARCIAL", "100", "RECHAZO", "PENDIENTE", "same");
        assertRejected(() -> insertRefund(payment, orderId, "PARCIAL", "100", "RECHAZO", "PENDIENTE", "same"));
    }

    @Test
    void refundsRejectUnknownTypeReasonStatusAndBadAmounts() {
        long payment = insertPayment(orderId, "fake", "tx-1", "APROBADO");
        assertRejected(() -> insertRefund(payment, orderId, "MEDIO", "100", "RECHAZO", "PENDIENTE", "a"));
    }

    @Test
    void refundsRejectUnknownReason() {
        long payment = insertPayment(orderId, "fake", "tx-1", "APROBADO");
        assertRejected(() -> insertRefund(payment, orderId, "PARCIAL", "100", "ANTOJO", "PENDIENTE", "b"));
    }

    @Test
    void refundsRejectUnknownStatus() {
        long payment = insertPayment(orderId, "fake", "tx-1", "APROBADO");
        assertRejected(() -> insertRefund(payment, orderId, "PARCIAL", "100", "RECHAZO", "TALVEZ", "c"));
    }

    @Test
    void refundsRejectZeroAmount() {
        long payment = insertPayment(orderId, "fake", "tx-1", "APROBADO");
        assertRejected(() -> insertRefund(payment, orderId, "PARCIAL", "0", "RECHAZO", "PENDIENTE", "d"));
    }

    @Test
    void refundsAcceptEveryReasonOfTheSpec() {
        long payment = insertPayment(orderId, "fake", "tx-1", "APROBADO");
        String[] reasons = {"RECHAZO", "CANCELACION", "VENCIMIENTO", "F17", "F18", "CLIENTE_AUSENTE"};
        for (int i = 0; i < reasons.length; i++) {
            insertRefund(payment, orderId, "PARCIAL", "100", reasons[i], "PENDIENTE", "r" + i);
        }
        assertEquals(reasons.length, jdbc.queryForObject("SELECT count(*) FROM refunds WHERE payment_id = ?", Integer.class, payment));
    }

    @Test
    void aPaymentWithRefundsCannotBeDeleted() {
        long payment = insertPayment(orderId, "fake", "tx-1", "APROBADO");
        insertRefund(payment, orderId, "TOTAL", "10000", "RECHAZO", "PENDIENTE", "k");
        assertRejected(() -> jdbc.update("DELETE FROM payments WHERE id = ?", payment));
    }

    // --------------------------- V9: order_policies ---------------------------

    @Test
    void migrationSeedsExactlyOneCompleteGlobalPolicy() {
        var row = jdbc.queryForMap("SELECT payment_expiration_minutes p, store_response_minutes s, "
                + "customer_cancel_window_minutes c FROM order_policies WHERE restaurant_id IS NULL");
        assertEquals(15, row.get("p"));
        assertEquals(10, row.get("s"));
        assertEquals(5, row.get("c"));
    }

    @Test
    void thereCanBeOnlyOneGlobalPolicy() {
        assertRejected(() -> jdbc.update("INSERT INTO order_policies(restaurant_id, payment_expiration_minutes, "
                + "store_response_minutes, customer_cancel_window_minutes) VALUES (NULL, 20, 20, 20)"));
    }

    @Test
    void aStorePolicyMayBePartialButOnlyOnePerStore() {
        jdbc.update("INSERT INTO order_policies(restaurant_id, store_response_minutes) VALUES (501, 30)");
        assertRejected(() -> jdbc.update("INSERT INTO order_policies(restaurant_id, store_response_minutes) VALUES (501, 40)"));
    }

    @Test
    void policiesRejectOutOfRangeMinutes() {
        assertRejected(() -> jdbc.update("INSERT INTO order_policies(restaurant_id, payment_expiration_minutes) VALUES (502, 0)"));
    }

    @Test
    void policiesRejectMoreThanADay() {
        assertRejected(() -> jdbc.update("INSERT INTO order_policies(restaurant_id, store_response_minutes) VALUES (503, 1441)"));
    }

    @Test
    void zeroMinutesIsValidForTheCancelWindow() {
        jdbc.update("INSERT INTO order_policies(restaurant_id, customer_cancel_window_minutes) VALUES (504, 0)");
        assertEquals(0, jdbc.queryForObject(
                "SELECT customer_cancel_window_minutes FROM order_policies WHERE restaurant_id = 504", Integer.class));
    }
}
