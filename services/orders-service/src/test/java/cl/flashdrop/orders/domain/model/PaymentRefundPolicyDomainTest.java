package cl.flashdrop.orders.domain.model;

import cl.flashdrop.orders.domain.exception.OrderDomainException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reglas de dominio de pagos, reembolsos (totales y parciales) y plazos parametricos
 * (FlashDrop 1.0 tiendas, spec 4.1). Sin base de datos ni Spring.
 */
class PaymentRefundPolicyDomainTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-16T12:00:00Z");
    private static final UUID ORDER = UUID.randomUUID();

    private static Payment openPayment() {
        return Payment.open(ORDER, "fake", "tx-1", BigDecimal.valueOf(10_000), NOW);
    }

    private static Payment approvedPayment() {
        Payment p = Payment.builder().id(1L).orderId(ORDER).provider("fake").providerTxId("tx-1")
                .amount(BigDecimal.valueOf(10_000)).status(PaymentStatus.PENDIENTE).createdAt(NOW).updatedAt(NOW).build();
        p.approve("OK", NOW);
        return p;
    }

    // ------------------------------- Payment -------------------------------

    @Test
    void newPaymentStartsPendingInClp() {
        Payment p = openPayment();
        assertEquals(PaymentStatus.PENDIENTE, p.getStatus());
        assertEquals("CLP", p.getCurrency());
    }

    @Test
    void paymentRequiresOrderProviderTransactionAndPositiveAmount() {
        assertThrows(OrderDomainException.class, () -> Payment.open(null, "fake", "tx", BigDecimal.ONE, NOW));
        assertThrows(OrderDomainException.class, () -> Payment.open(ORDER, " ", "tx", BigDecimal.ONE, NOW));
        assertThrows(OrderDomainException.class, () -> Payment.open(ORDER, "fake", null, BigDecimal.ONE, NOW));
        assertThrows(OrderDomainException.class, () -> Payment.open(ORDER, "fake", "tx", BigDecimal.ZERO, NOW));
        assertThrows(OrderDomainException.class, () -> Payment.open(ORDER, "fake", "tx", BigDecimal.valueOf(-1), NOW));
    }

    @Test
    void repeatingTheSameResultIsIdempotent() {
        Payment p = openPayment();
        p.approve("first", NOW);
        p.approve("webhook-again", NOW.plusMinutes(1));

        assertEquals(PaymentStatus.APROBADO, p.getStatus());
        assertEquals("first", p.getRawStatus());
    }

    @Test
    void aResolvedPaymentCannotChangeItsResult() {
        Payment approved = openPayment();
        approved.approve("OK", NOW);
        assertThrows(OrderDomainException.class, () -> approved.reject("late", NOW));

        Payment rejected = openPayment();
        rejected.reject("NO", NOW);
        assertThrows(OrderDomainException.class, () -> rejected.approve("late", NOW));
        assertThrows(OrderDomainException.class, () -> rejected.expire("late", NOW));
    }

    // ------------------------------- Refund --------------------------------

    @Test
    void totalRefundMustReturnEverythingChargedAndOnlyOnce() {
        Payment p = approvedPayment();

        Refund ok = Refund.open(p, RefundType.TOTAL, BigDecimal.valueOf(10_000), RefundReason.RECHAZO,
                BigDecimal.ZERO, "key-1", NOW);
        assertEquals(RefundStatus.PENDIENTE, ok.getStatus());
        assertEquals(ORDER, ok.getOrderId());

        assertThrows(OrderDomainException.class, () -> Refund.open(p, RefundType.TOTAL, BigDecimal.valueOf(9_000),
                RefundReason.RECHAZO, BigDecimal.ZERO, "key-2", NOW), "total con monto menor");
        assertThrows(OrderDomainException.class, () -> Refund.open(p, RefundType.TOTAL, BigDecimal.valueOf(10_000),
                RefundReason.RECHAZO, BigDecimal.valueOf(1_000), "key-3", NOW), "total habiendo devuelto algo antes");
    }

    @Test
    void partialRefundsAddUpButNeverExceedWhatWasCharged() {
        Payment p = approvedPayment();

        Refund first = Refund.open(p, RefundType.PARCIAL, BigDecimal.valueOf(2_500), RefundReason.CLIENTE_AUSENTE,
                BigDecimal.ZERO, "key-a", NOW);
        assertEquals(RefundType.PARCIAL, first.getType());

        // Quedan 7.500: devolver 7.500 mas esta bien; 7.501 no.
        assertDoesNotThrow(() -> Refund.open(p, RefundType.PARCIAL, BigDecimal.valueOf(7_500),
                RefundReason.CANCELACION, BigDecimal.valueOf(2_500), "key-b", NOW));
        assertThrows(OrderDomainException.class, () -> Refund.open(p, RefundType.PARCIAL, BigDecimal.valueOf(7_501),
                RefundReason.CANCELACION, BigDecimal.valueOf(2_500), "key-c", NOW));
    }

    @Test
    void refundRequiresAnApprovedPersistedPaymentAndAPositiveAmount() {
        Payment pending = Payment.builder().id(1L).orderId(ORDER).provider("fake").providerTxId("t")
                .amount(BigDecimal.TEN).status(PaymentStatus.PENDIENTE).build();
        assertThrows(OrderDomainException.class, () -> Refund.open(pending, RefundType.TOTAL, BigDecimal.TEN,
                RefundReason.RECHAZO, BigDecimal.ZERO, "k", NOW), "pago no aprobado");

        Payment unsaved = openPayment();
        unsaved.approve("OK", NOW);
        assertThrows(OrderDomainException.class, () -> Refund.open(unsaved, RefundType.TOTAL, BigDecimal.valueOf(10_000),
                RefundReason.RECHAZO, BigDecimal.ZERO, "k", NOW), "pago sin id");

        Payment p = approvedPayment();
        assertThrows(OrderDomainException.class, () -> Refund.open(p, RefundType.PARCIAL, BigDecimal.ZERO,
                RefundReason.RECHAZO, BigDecimal.ZERO, "k", NOW));
        assertThrows(OrderDomainException.class, () -> Refund.open(p, RefundType.PARCIAL, BigDecimal.ONE,
                RefundReason.RECHAZO, BigDecimal.ZERO, " ", NOW), "sin llave de idempotencia");
    }

    @Test
    void failedRefundCountsAttemptsTruncatesTheErrorAndCanStillSucceed() {
        Refund r = Refund.open(approvedPayment(), RefundType.TOTAL, BigDecimal.valueOf(10_000), RefundReason.F18,
                BigDecimal.ZERO, "key-r", NOW);

        r.fail("x".repeat(900), NOW.plusMinutes(5), NOW);
        r.fail("timeout", NOW.plusMinutes(10), NOW);

        assertEquals(RefundStatus.FALLIDO, r.getStatus());
        assertEquals(2, r.getAttempts());
        assertEquals("timeout", r.getLastError());
        assertEquals(NOW.plusMinutes(10), r.getNextAttemptAt());

        r.succeed("prov-77", NOW.plusMinutes(11));
        assertEquals(RefundStatus.HECHO, r.getStatus());
        assertEquals("prov-77", r.getProviderRefundId());
        assertNull(r.getLastError());
        assertNull(r.getNextAttemptAt());

        // Un reembolso hecho no se puede marcar como fallido ni se repite.
        assertThrows(OrderDomainException.class, () -> r.fail("late", null, NOW));
        assertDoesNotThrow(() -> r.succeed("again", NOW));
        assertEquals("prov-77", r.getProviderRefundId());
    }

    @Test
    void longErrorsAreCutToTheColumnSize() {
        Refund r = Refund.open(approvedPayment(), RefundType.TOTAL, BigDecimal.valueOf(10_000), RefundReason.F18,
                BigDecimal.ZERO, "key-r", NOW);
        r.fail("x".repeat(900), NOW, NOW);
        assertEquals(500, r.getLastError().length());
    }

    // ------------------------------ OrderPolicy ----------------------------

    private static OrderPolicy global(int pay, int store, int cancel) {
        return OrderPolicy.builder().paymentExpirationMinutes(pay).storeResponseMinutes(store)
                .customerCancelWindowMinutes(cancel).build();
    }

    @Test
    void storeWithoutOverridesInheritsEverythingFromGlobal() {
        OrderPolicy.Effective e = OrderPolicy.resolve(global(15, 10, 5), null);
        assertEquals(new OrderPolicy.Effective(15, 10, 5), e);
    }

    @Test
    void storeOverridesOnlyWhatItSetsAndInheritsTheRest() {
        OrderPolicy store = OrderPolicy.builder().restaurantId(UUID.randomUUID())
                .storeResponseMinutes(20).customerCancelWindowMinutes(0).build();

        OrderPolicy.Effective e = OrderPolicy.resolve(global(15, 10, 5), store);

        assertEquals(15, e.paymentExpirationMinutes());
        assertEquals(20, e.storeResponseMinutes());
        // 0 es un valor valido ("no se puede cancelar") y NO debe confundirse con "no configurado".
        assertEquals(0, e.customerCancelWindowMinutes());
    }

    @Test
    void policyValidationRejectsOutOfRangeValuesAndIncompleteGlobal() {
        assertDoesNotThrow(() -> global(1, 1, 0).validate());
        assertDoesNotThrow(() -> global(1440, 1440, 1440).validate());
        assertThrows(OrderDomainException.class, () -> global(0, 10, 5).validate());
        assertThrows(OrderDomainException.class, () -> global(15, 1441, 5).validate());
        assertThrows(OrderDomainException.class, () -> global(15, 10, -1).validate());

        OrderPolicy incompleteGlobal = OrderPolicy.builder().paymentExpirationMinutes(15).build();
        assertThrows(OrderDomainException.class, incompleteGlobal::validate);

        OrderPolicy partialStore = OrderPolicy.builder().restaurantId(UUID.randomUUID()).storeResponseMinutes(20).build();
        assertDoesNotThrow(partialStore::validate);
    }
}
