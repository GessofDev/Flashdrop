package cl.flashdrop.orders.application.usecase;

import cl.flashdrop.orders.application.service.OrderPolicyResolver;
import cl.flashdrop.orders.domain.exception.OrderDomainException;
import cl.flashdrop.orders.domain.model.DeliveryRoute;
import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.model.OrderPolicy;
import cl.flashdrop.orders.domain.model.OrderStatus;
import cl.flashdrop.orders.domain.model.Payment;
import cl.flashdrop.orders.domain.model.PaymentMethod;
import cl.flashdrop.orders.domain.model.PaymentStatus;
import cl.flashdrop.orders.domain.model.ReceptionMode;
import cl.flashdrop.orders.domain.model.RestaurantInfo;
import cl.flashdrop.orders.domain.port.CatalogPort;
import cl.flashdrop.orders.domain.port.ClientPort;
import cl.flashdrop.orders.domain.port.DeliveryPort;
import cl.flashdrop.orders.domain.port.EventPublisherPort;
import cl.flashdrop.orders.domain.port.OrderRepositoryPort;
import cl.flashdrop.orders.domain.port.PaymentRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConfirmPaymentUseCaseTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-19T12:00:00Z");

    @Mock private PaymentRepositoryPort paymentRepository;
    @Mock private OrderRepositoryPort orderRepository;
    @Mock private CatalogPort catalogPort;
    @Mock private ClientPort clientPort;
    @Mock private DeliveryPort deliveryPort;
    @Mock private EventPublisherPort eventPublisher;
    @Mock private OrderPolicyResolver policyResolver;

    private ConfirmPaymentUseCase useCase;

    private final UUID orderId = UUID.randomUUID();
    private final UUID restaurantId = UUID.randomUUID();
    private final UUID clientId = UUID.randomUUID();
    private final UUID clientUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new ConfirmPaymentUseCase(paymentRepository, orderRepository, catalogPort, clientPort,
                deliveryPort, eventPublisher, policyResolver, Clock.fixed(NOW.toInstant(), ZoneOffset.UTC));
        ReflectionTestUtils.setField(useCase, "defaultDistanceKm", BigDecimal.valueOf(3.2));
        ReflectionTestUtils.setField(useCase, "defaultEstimatedMinutes", 20);
        ReflectionTestUtils.setField(useCase, "orderCreatedRoutingKey", "order.created");
    }

    private Payment pendingPayment() {
        return Payment.open(orderId, "FAKE", "tx-1", BigDecimal.valueOf(3500), NOW.minusMinutes(2));
    }

    private Order order(OrderStatus status) {
        return Order.builder().id(orderId).clientId(clientId).restaurantId(restaurantId).status(status)
                .address("Av. Providencia 1200").commune("Providencia").total(BigDecimal.valueOf(3500))
                .distanceKm(new BigDecimal("4.50")).estimatedMinutes(25)
                .paymentMethod(PaymentMethod.TARJETA).receptionMode(ReceptionMode.SEGURO).build();
    }

    private void stubFound(Payment payment, Order order) {
        when(paymentRepository.findByProviderAndProviderTxId("FAKE", "tx-1")).thenReturn(Optional.of(payment));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
    }

    @Test
    void pagoAprobadoHaceQueLaTiendaTomeElPedidoFijaLaEsperaDeRepartidorCreaLaRutaYPublicaElEvento() {
        Payment payment = pendingPayment();
        stubFound(payment, order(OrderStatus.PENDIENTE_PAGO));
        when(policyResolver.effectiveFor(restaurantId)).thenReturn(new OrderPolicy.Effective(15, 10, 5, 20));
        when(orderRepository.confirmPayment(orderId, NOW, NOW.plusMinutes(20))).thenReturn(true);
        when(catalogPort.findRestaurantById(restaurantId)).thenReturn(Optional.of(RestaurantInfo.builder()
                .restaurantId(restaurantId).name("Burgers House").address("Los Leones 300")
                .latitude(new BigDecimal("-33.4")).longitude(new BigDecimal("-70.6")).build()));
        when(clientPort.findUserIdByClientId(clientId)).thenReturn(Optional.of(clientUserId));

        ConfirmPaymentUseCase.Outcome outcome = useCase.execute("FAKE", "tx-1", true, "APPROVED");

        assertEquals(ConfirmPaymentUseCase.Outcome.ORDER_ACTIVATED, outcome);
        assertEquals(PaymentStatus.APROBADO, payment.getStatus());
        verify(paymentRepository).save(payment);
        ArgumentCaptor<DeliveryRoute> route = ArgumentCaptor.forClass(DeliveryRoute.class);
        verify(deliveryPort).saveRoute(route.capture());
        assertEquals(orderId, route.getValue().getOrderId());
        assertEquals("Burgers House, Los Leones 300", route.getValue().getPickupAddress());
        assertEquals(0, new BigDecimal("4.50").compareTo(route.getValue().getDistanceKm()));
        assertEquals(25, route.getValue().getEstimatedMinutes());
        assertEquals(clientUserId, route.getValue().getClientUserId());
        verify(eventPublisher).publish(eq("order.created"), any());
    }

    @Test
    void pagoRechazadoDejaElPedidoPendienteSinRutaNiEvento() {
        Payment payment = pendingPayment();
        stubFound(payment, order(OrderStatus.PENDIENTE_PAGO));

        ConfirmPaymentUseCase.Outcome outcome = useCase.execute("FAKE", "tx-1", false, "REJECTED");

        assertEquals(ConfirmPaymentUseCase.Outcome.PAYMENT_REJECTED, outcome);
        assertEquals(PaymentStatus.RECHAZADO, payment.getStatus());
        verify(orderRepository, never()).confirmPayment(any(), any(), any());
        verifyNoInteractions(deliveryPort, eventPublisher);
    }

    @Test
    void elAvisoAprobadoRepetidoNoVuelveACrearLaRutaNiElEvento() {
        Payment payment = pendingPayment();
        payment.approve("APPROVED", NOW.minusMinutes(1));
        stubFound(payment, order(OrderStatus.NUEVO_PEDIDO));

        ConfirmPaymentUseCase.Outcome outcome = useCase.execute("FAKE", "tx-1", true, "APPROVED");

        assertEquals(ConfirmPaymentUseCase.Outcome.ALREADY_CONFIRMED, outcome);
        verify(orderRepository, never()).confirmPayment(any(), any(), any());
        verifyNoInteractions(deliveryPort, eventPublisher);
    }

    @Test
    void pagoAprobadoDeUnPedidoCanceladoNoLoReactiva() {
        stubFound(pendingPayment(), order(OrderStatus.CANCELADO));

        ConfirmPaymentUseCase.Outcome outcome = useCase.execute("FAKE", "tx-1", true, "APPROVED");

        assertEquals(ConfirmPaymentUseCase.Outcome.ORDER_NOT_PENDING, outcome);
        verify(orderRepository, never()).confirmPayment(any(), any(), any());
        verifyNoInteractions(deliveryPort, eventPublisher);
    }

    @Test
    void siElPedidoDejoDeEstarPendienteEnMedioDeLaConfirmacionNoSeCreaLaRuta() {
        stubFound(pendingPayment(), order(OrderStatus.PENDIENTE_PAGO));
        when(policyResolver.effectiveFor(restaurantId)).thenReturn(new OrderPolicy.Effective(15, 10, 5, 20));
        when(orderRepository.confirmPayment(any(), any(), any())).thenReturn(false);

        assertEquals(ConfirmPaymentUseCase.Outcome.ORDER_NOT_PENDING, useCase.execute("FAKE", "tx-1", true, "APPROVED"));
        verifyNoInteractions(deliveryPort, eventPublisher);
    }

    @Test
    void unPagoAprobadoNoPuedePasarARechazado() {
        Payment payment = pendingPayment();
        payment.approve("APPROVED", NOW);
        stubFound(payment, order(OrderStatus.NUEVO_PEDIDO));

        assertThrows(OrderDomainException.class, () -> useCase.execute("FAKE", "tx-1", false, "REJECTED"));
        assertEquals(PaymentStatus.APROBADO, payment.getStatus());
    }

    @Test
    void pagoInexistenteDa404() {
        when(paymentRepository.findByProviderAndProviderTxId("FAKE", "nope")).thenReturn(Optional.empty());

        OrderDomainException ex = assertThrows(OrderDomainException.class,
                () -> useCase.execute("FAKE", "nope", true, "APPROVED"));
        assertTrue(ex.getMessage().toLowerCase().contains("no encontrado"));
    }
}
