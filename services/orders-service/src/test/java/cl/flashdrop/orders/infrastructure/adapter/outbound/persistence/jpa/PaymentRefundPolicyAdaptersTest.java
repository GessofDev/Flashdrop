package cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa;

import cl.flashdrop.orders.application.service.OrderPolicyResolver;
import cl.flashdrop.orders.domain.exception.OrderDomainException;
import cl.flashdrop.orders.domain.model.*;
import cl.flashdrop.orders.infrastructure.adapter.outbound.IdConverter;
import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.entity.ClientEntity;
import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Adaptadores de pagos, reembolsos y politicas (FlashDrop 1.0 tiendas, spec 4.1) contra
 * PostgreSQL real con las migraciones V1 a V9.
 */
class PaymentRefundPolicyAdaptersTest extends PostgresIntegrationTestSupport {

    @Autowired private SpringDataOrderRepository orderRepository;
    @Autowired private SpringDataOrderItemRepository orderItemRepository;
    @Autowired private SpringDataOrderDeliveryCodeAttemptRepository attemptRepository;
    @Autowired private SpringDataClientRepository clientRepository;
    @Autowired private SpringDataPaymentRepository paymentRepository;
    @Autowired private SpringDataRefundRepository refundRepository;
    @Autowired private SpringDataOrderPolicyRepository policyRepository;

    private JpaOrderRepositoryAdapter orders;
    private JpaPaymentRepositoryAdapter payments;
    private JpaRefundRepositoryAdapter refunds;
    private JpaOrderPolicyRepositoryAdapter policies;

    private UUID orderId;

    @BeforeEach
    void setUp() {
        orders = new JpaOrderRepositoryAdapter(orderRepository, orderItemRepository, attemptRepository);
        payments = new JpaPaymentRepositoryAdapter(paymentRepository);
        refunds = new JpaRefundRepositoryAdapter(refundRepository);
        policies = new JpaOrderPolicyRepositoryAdapter(policyRepository);

        ClientEntity client = clientRepository.save(ClientEntity.builder()
                .userId(System.nanoTime()).createdAt(OffsetDateTime.now()).build());
        orderId = orders.save(Order.builder()
                .clientId(IdConverter.toUuid(client.getId())).restaurantId(IdConverter.toUuid(7L))
                .status(OrderStatus.PENDIENTE_PAGO).address("Calle 1")
                .subtotal(BigDecimal.valueOf(7500)).deliveryFee(BigDecimal.valueOf(2500)).total(BigDecimal.valueOf(10000))
                .paymentMethod(PaymentMethod.TARJETA).items(List.of()).build()).getId();
    }

    private Payment savedApprovedPayment() {
        Payment p = payments.save(Payment.open(orderId, "fake", "tx-" + System.nanoTime(),
                BigDecimal.valueOf(10_000), OffsetDateTime.now()));
        p.approve("OK", OffsetDateTime.now());
        return payments.save(p);
    }

    // ------------------------------- pagos -------------------------------

    @Test
    void paymentRoundTripKeepsEveryField() {
        Payment saved = payments.save(Payment.open(orderId, "webpay", "tx-abc", BigDecimal.valueOf(10_000), OffsetDateTime.now()));

        Payment found = payments.findById(saved.getId()).orElseThrow();
        assertEquals(orderId, found.getOrderId());
        assertEquals("webpay", found.getProvider());
        assertEquals("tx-abc", found.getProviderTxId());
        assertEquals(0, BigDecimal.valueOf(10_000).compareTo(found.getAmount()));
        assertEquals("CLP", found.getCurrency().trim());
        assertEquals(PaymentStatus.PENDIENTE, found.getStatus());
    }

    @Test
    void approvedStatusIsPersistedAndFoundByProviderTransaction() {
        Payment p = payments.save(Payment.open(orderId, "fake", "tx-1", BigDecimal.valueOf(10_000), OffsetDateTime.now()));
        p.approve("AUTHORIZED", OffsetDateTime.now());
        payments.save(p);

        Payment found = payments.findByProviderAndProviderTxId("fake", "tx-1").orElseThrow();
        assertEquals(PaymentStatus.APROBADO, found.getStatus());
        assertEquals("AUTHORIZED", found.getRawStatus());
        assertTrue(payments.findByProviderAndProviderTxId("fake", "otra").isEmpty());
        assertTrue(payments.findByProviderAndProviderTxId(null, "tx-1").isEmpty());
    }

    @Test
    void paymentsOfAnOrderComeNewestFirst() {
        Payment first = payments.save(Payment.open(orderId, "fake", "tx-1", BigDecimal.TEN, OffsetDateTime.now()));
        first.reject("NO", OffsetDateTime.now());
        payments.save(first);
        // El intento anterior debe quedar cerrado en la base ANTES de abrir el siguiente: el
        // indice "un solo pago abierto por pedido" no admite dos PENDIENTE ni por un instante.
        paymentRepository.flush();
        Payment second = payments.save(Payment.open(orderId, "fake", "tx-2", BigDecimal.TEN, OffsetDateTime.now()));

        List<Payment> found = payments.findByOrderId(orderId);

        assertEquals(List.of(second.getId(), first.getId()), found.stream().map(Payment::getId).toList());
        assertTrue(payments.findByOrderId(null).isEmpty());
    }

    @Test
    void theDatabaseStillBlocksADoubleApprovalThroughTheAdapter() {
        savedApprovedPayment();

        assertThrows(DataIntegrityViolationException.class, () -> {
            Payment second = payments.save(Payment.open(orderId, "fake", "tx-other", BigDecimal.TEN, OffsetDateTime.now()));
            second.approve("OK", OffsetDateTime.now());
            payments.save(second);
            paymentRepository.flush();
        });
    }

    // ----------------------------- reembolsos -----------------------------

    @Test
    void refundRoundTripAndFailureBookkeeping() {
        Payment payment = savedApprovedPayment();
        Refund refund = Refund.open(payment, RefundType.TOTAL, BigDecimal.valueOf(10_000), RefundReason.F18,
                BigDecimal.ZERO, "refund-key-1", OffsetDateTime.now());
        Refund saved = refunds.save(refund);

        saved.fail("pasarela caida", OffsetDateTime.now().plusMinutes(5), OffsetDateTime.now());
        refunds.save(saved);

        Refund found = refunds.findById(saved.getId()).orElseThrow();
        assertEquals(RefundStatus.FALLIDO, found.getStatus());
        assertEquals(RefundType.TOTAL, found.getType());
        assertEquals(RefundReason.F18, found.getReason());
        assertEquals(1, found.getAttempts());
        assertEquals("pasarela caida", found.getLastError());
        assertNotNull(found.getNextAttemptAt());
        assertEquals(List.of(found.getId()), refunds.findByOrderId(orderId).stream().map(Refund::getId).toList());
    }

    @Test
    void partialRefundsAreSummedAndTheDomainRuleStopsOverRefunding() {
        Payment payment = savedApprovedPayment();
        assertEquals(0, BigDecimal.ZERO.compareTo(refunds.sumCommittedByPaymentId(payment.getId())));

        refunds.save(Refund.open(payment, RefundType.PARCIAL, BigDecimal.valueOf(2_500), RefundReason.CLIENTE_AUSENTE,
                refunds.sumCommittedByPaymentId(payment.getId()), "k-1", OffsetDateTime.now()));
        refunds.save(Refund.open(payment, RefundType.PARCIAL, BigDecimal.valueOf(4_000), RefundReason.CANCELACION,
                refunds.sumCommittedByPaymentId(payment.getId()), "k-2", OffsetDateTime.now()));

        BigDecimal committed = refunds.sumCommittedByPaymentId(payment.getId());
        assertEquals(0, BigDecimal.valueOf(6_500).compareTo(committed));
        // Quedan 3.500: pedir 3.501 lo rechaza el dominio antes de llegar a la base.
        assertThrows(OrderDomainException.class, () -> Refund.open(payment, RefundType.PARCIAL,
                BigDecimal.valueOf(3_501), RefundReason.CANCELACION, committed, "k-3", OffsetDateTime.now()));
        assertEquals(0, refunds.sumCommittedByPaymentId(null).signum());
    }

    // ------------------------------ politicas ------------------------------

    @Test
    void globalPolicyComesFromTheMigrationSeed() {
        OrderPolicy global = policies.findGlobal().orElseThrow();
        assertNull(global.getRestaurantId());
        assertEquals(15, global.getPaymentExpirationMinutes());
        assertEquals(10, global.getStoreResponseMinutes());
        assertEquals(5, global.getCustomerCancelWindowMinutes());
    }

    @Test
    void adminCanChangeTheGlobalPolicyWithoutCreatingASecondRow() {
        policies.save(OrderPolicy.builder().paymentExpirationMinutes(30).storeResponseMinutes(12)
                .customerCancelWindowMinutes(3).build(), IdConverter.toUuid(1L));
        policyRepository.flush();

        OrderPolicy global = policies.findGlobal().orElseThrow();
        assertEquals(30, global.getPaymentExpirationMinutes());
        assertEquals(1, policyRepository.findAll().stream().filter(p -> p.getRestaurantId() == null).count());
    }

    @Test
    void storeOwnerPolicyIsPartialAndInheritsTheRestThroughTheResolver() {
        UUID store = IdConverter.toUuid(7L);
        policies.save(OrderPolicy.builder().restaurantId(store).customerCancelWindowMinutes(0).build(),
                IdConverter.toUuid(5L));
        policyRepository.flush();

        OrderPolicyResolver resolver = new OrderPolicyResolver(policies);

        assertEquals(new OrderPolicy.Effective(15, 10, 0), resolver.effectiveFor(store));
        // Otra tienda sin configuracion propia usa el global.
        assertEquals(new OrderPolicy.Effective(15, 10, 5), resolver.effectiveFor(IdConverter.toUuid(8L)));
        assertEquals(new OrderPolicy.Effective(15, 10, 5), resolver.effectiveFor(null));
    }

    @Test
    void savingTheSameStorePolicyTwiceUpdatesInsteadOfDuplicating() {
        UUID store = IdConverter.toUuid(9L);
        policies.save(OrderPolicy.builder().restaurantId(store).storeResponseMinutes(20).build(), null);
        policies.save(OrderPolicy.builder().restaurantId(store).storeResponseMinutes(25).build(), null);
        policyRepository.flush();

        Optional<OrderPolicy> found = policies.findByRestaurantId(store);
        assertEquals(25, found.orElseThrow().getStoreResponseMinutes());
        assertEquals(1, policyRepository.findAll().stream().filter(p -> IdConverter.toLong(store) == (p.getRestaurantId() == null ? -1 : p.getRestaurantId())).count());
    }

    @Test
    void invalidPoliciesAreRejectedBeforeReachingTheDatabase() {
        assertThrows(OrderDomainException.class, () -> policies.save(
                OrderPolicy.builder().restaurantId(IdConverter.toUuid(7L)).paymentExpirationMinutes(0).build(), null));
    }
}
