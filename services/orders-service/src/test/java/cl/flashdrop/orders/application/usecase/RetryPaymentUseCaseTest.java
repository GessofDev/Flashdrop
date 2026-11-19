package cl.flashdrop.orders.application.usecase;

import cl.flashdrop.orders.domain.exception.ForbiddenOperationException;
import cl.flashdrop.orders.domain.exception.OrderDomainException;
import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.model.OrderStatus;
import cl.flashdrop.orders.domain.model.Payment;
import cl.flashdrop.orders.domain.port.ClientPort;
import cl.flashdrop.orders.domain.port.OrderRepositoryPort;
import cl.flashdrop.orders.domain.port.PaymentGatewayPort;
import cl.flashdrop.orders.domain.port.PaymentRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RetryPaymentUseCaseTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-19T12:00:00Z");

    @Mock private OrderRepositoryPort orderRepository;
    @Mock private ClientPort clientPort;
    @Mock private PaymentRepositoryPort paymentRepository;
    @Mock private PaymentGatewayPort paymentGateway;

    private RetryPaymentUseCase useCase;

    private final UUID orderId = UUID.randomUUID();
    private final UUID clientId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new RetryPaymentUseCase(orderRepository, clientPort, paymentRepository, paymentGateway,
                Clock.fixed(NOW.toInstant(), ZoneOffset.UTC));
        lenient().when(clientPort.findClientIdByUserId(userId)).thenReturn(Optional.of(clientId));
    }

    private Order order(OrderStatus status, OffsetDateTime expiresAt) {
        return Order.builder().id(orderId).clientId(clientId).status(status).total(BigDecimal.valueOf(3500))
                .paymentExpiresAt(expiresAt).build();
    }

    @Test
    void trasUnPagoRechazadoAbreUnNuevoIntentoPorElMismoTotal() {
        when(orderRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(order(OrderStatus.PENDIENTE_PAGO, NOW.plusMinutes(5))));
        Payment rejected = Payment.open(orderId, "FAKE", "tx-old", BigDecimal.valueOf(3500), NOW.minusMinutes(3));
        rejected.reject("REJECTED", NOW.minusMinutes(2));
        when(paymentRepository.findByOrderId(orderId)).thenReturn(List.of(rejected));
        when(paymentGateway.providerName()).thenReturn("FAKE");
        when(paymentGateway.createSession(orderId, BigDecimal.valueOf(3500), "CLP"))
                .thenReturn(new PaymentGatewayPort.PaymentSession("tx-new", "fake://flashdrop-pay/tx-new"));
        when(paymentGateway.paymentUrlFor("tx-new")).thenReturn("fake://flashdrop-pay/tx-new");
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        RetryPaymentUseCase.OpenPayment open = useCase.execute(orderId, userId);

        assertEquals("fake://flashdrop-pay/tx-new", open.paymentUrl());
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void siYaHayUnCobroAbiertoSeDevuelveEseSinAbrirOtro() {
        when(orderRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(order(OrderStatus.PENDIENTE_PAGO, NOW.plusMinutes(5))));
        when(paymentRepository.findByOrderId(orderId)).thenReturn(List.of(
                Payment.open(orderId, "FAKE", "tx-open", BigDecimal.valueOf(3500), NOW)));
        when(paymentGateway.paymentUrlFor("tx-open")).thenReturn("fake://flashdrop-pay/tx-open");

        RetryPaymentUseCase.OpenPayment open = useCase.execute(orderId, userId);

        assertEquals("fake://flashdrop-pay/tx-open", open.paymentUrl());
        verify(paymentGateway, never()).createSession(any(), any(), any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void soloElClienteDuenoPuedeReintentar() {
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(
                Order.builder().id(orderId).clientId(UUID.randomUUID()).status(OrderStatus.PENDIENTE_PAGO).build()));

        assertThrows(ForbiddenOperationException.class, () -> useCase.execute(orderId, userId));
    }

    @Test
    void unPedidoQueYaNoEstaPendienteONoVencioNoSeReintenta() {
        when(orderRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(order(OrderStatus.NUEVO_PEDIDO, null)));
        assertThrows(OrderDomainException.class, () -> useCase.execute(orderId, userId));

        when(orderRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(order(OrderStatus.PENDIENTE_PAGO, NOW.minusMinutes(1))));
        OrderDomainException ex = assertThrows(OrderDomainException.class, () -> useCase.execute(orderId, userId));
        assertTrue(ex.getMessage().contains("vencio"));
        verify(paymentGateway, never()).createSession(any(), any(), any());
    }
}
