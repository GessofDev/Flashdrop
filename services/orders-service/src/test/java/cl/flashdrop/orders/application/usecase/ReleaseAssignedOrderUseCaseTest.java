package cl.flashdrop.orders.application.usecase;

import cl.flashdrop.orders.domain.exception.ForbiddenOperationException;
import cl.flashdrop.orders.domain.exception.OrderDomainException;
import cl.flashdrop.orders.domain.model.DeliveryOperation;
import cl.flashdrop.orders.domain.model.DeliveryOperationState;
import cl.flashdrop.orders.domain.model.DeliveryOperationType;
import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.model.OrderStatus;
import cl.flashdrop.orders.domain.model.PaymentMethod;
import cl.flashdrop.orders.domain.model.Role;
import cl.flashdrop.orders.domain.port.DeliveryOperationRepositoryPort;
import cl.flashdrop.orders.domain.port.DeliveryPort;
import cl.flashdrop.orders.domain.port.OrderRepositoryPort;
import cl.flashdrop.orders.infrastructure.exception.ExternalServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReleaseAssignedOrderUseCaseTest {

    private static final UUID ORDER_ID = UUID.fromString("00000000-0000-0000-0000-000000000065");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID OTHER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");
    private static final UUID DELIVERY_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID OTHER_DELIVERY_ID = UUID.fromString("00000000-0000-0000-0000-000000000008");
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-05T12:00:00Z");

    @Mock
    private OrderRepositoryPort orderRepository;

    @Mock
    private DeliveryPort deliveryPort;

    @Mock
    private DeliveryOperationRepositoryPort deliveryOperationRepository;

    private ReleaseAssignedOrderUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ReleaseAssignedOrderUseCase(orderRepository, deliveryPort, deliveryOperationRepository);
    }

    @Test
    void repartidorDuenioLiberaPedidoEnListoParaRetiroConExito() {
        Order order = buildOrder(OrderStatus.LISTO_PARA_RETIRO, DELIVERY_ID);
        when(deliveryPort.findDeliveryIdByUserId(USER_ID)).thenReturn(Optional.of(DELIVERY_ID));
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

        DeliveryOperation pendingOp = DeliveryOperation.pending(
                ORDER_ID, DeliveryOperationType.RELEASE, USER_ID, Role.REPARTIDOR,
                "Pinchadura de neumatico", null, DELIVERY_ID, OrderStatus.LISTO_PARA_RETIRO, "corr-1", NOW)
                .withId(101L);
        when(deliveryOperationRepository.create(any(DeliveryOperation.class))).thenReturn(pendingOp);
        when(deliveryOperationRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(pendingOp));

        useCase.execute(ORDER_ID, USER_ID, "Pinchadura de neumatico", "corr-1");

        verify(deliveryPort).releaseRoute(ORDER_ID, DELIVERY_ID);
        verify(orderRepository).save(order);
        assertThat(order.getDeliveryId()).isNull();

        ArgumentCaptor<DeliveryOperation> opCaptor = ArgumentCaptor.forClass(DeliveryOperation.class);
        verify(deliveryOperationRepository).save(opCaptor.capture());
        assertThat(opCaptor.getValue().state()).isEqualTo(DeliveryOperationState.SUCCEEDED);
        assertThat(opCaptor.getValue().resultingStatus()).isEqualTo(OrderStatus.LISTO_PARA_RETIRO);
    }

    @Test
    void repartidorNoDuenioNoPuedeLiberarPedido() {
        Order order = buildOrder(OrderStatus.LISTO_PARA_RETIRO, OTHER_DELIVERY_ID);
        when(deliveryPort.findDeliveryIdByUserId(USER_ID)).thenReturn(Optional.of(DELIVERY_ID));
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

        assertThrows(ForbiddenOperationException.class,
                () -> useCase.execute(ORDER_ID, USER_ID, "Pinchadura", "corr-1"));

        verify(deliveryPort, never()).releaseRoute(any(), any());
        verify(deliveryOperationRepository, never()).create(any());
    }

    @Test
    void noSePuedeLiberarPedidoEnEstadoDistintoDeListoParaRetiro() {
        Order order = buildOrder(OrderStatus.RETIRADO, DELIVERY_ID);
        when(deliveryPort.findDeliveryIdByUserId(USER_ID)).thenReturn(Optional.of(DELIVERY_ID));
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

        assertThrows(OrderDomainException.class,
                () -> useCase.execute(ORDER_ID, USER_ID, "Pinchadura", "corr-1"));

        verify(deliveryPort, never()).releaseRoute(any(), any());
        verify(deliveryOperationRepository, never()).create(any());
    }

    @Test
    void fallaDefinitivaDeDeliveryMarcaOperacionFallidaYNoModificaPedido() {
        Order order = buildOrder(OrderStatus.LISTO_PARA_RETIRO, DELIVERY_ID);
        when(deliveryPort.findDeliveryIdByUserId(USER_ID)).thenReturn(Optional.of(DELIVERY_ID));
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

        DeliveryOperation pendingOp = DeliveryOperation.pending(
                ORDER_ID, DeliveryOperationType.RELEASE, USER_ID, Role.REPARTIDOR,
                "Pinchadura", null, DELIVERY_ID, OrderStatus.LISTO_PARA_RETIRO, "corr-1", NOW)
                .withId(101L);
        when(deliveryOperationRepository.create(any(DeliveryOperation.class))).thenReturn(pendingOp);
        when(deliveryOperationRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(pendingOp));

        ExternalServiceException conflict = new ExternalServiceException(HttpStatus.CONFLICT, "Route conflict");
        org.mockito.Mockito.doThrow(conflict).when(deliveryPort).releaseRoute(ORDER_ID, DELIVERY_ID);

        assertThrows(ExternalServiceException.class,
                () -> useCase.execute(ORDER_ID, USER_ID, "Pinchadura", "corr-1"));

        assertThat(order.getDeliveryId()).isEqualTo(DELIVERY_ID);
        verify(orderRepository, never()).save(order);

        ArgumentCaptor<DeliveryOperation> opCaptor = ArgumentCaptor.forClass(DeliveryOperation.class);
        verify(deliveryOperationRepository).save(opCaptor.capture());
        assertThat(opCaptor.getValue().state()).isEqualTo(DeliveryOperationState.FAILED);
        assertThat(opCaptor.getValue().failureCode()).isEqualTo("CONFLICT");
    }

    @Test
    void fallaDeRedEnDeliveryDejaOperacionPendienteParaRecuperacion() {
        Order order = buildOrder(OrderStatus.LISTO_PARA_RETIRO, DELIVERY_ID);
        when(deliveryPort.findDeliveryIdByUserId(USER_ID)).thenReturn(Optional.of(DELIVERY_ID));
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

        DeliveryOperation pendingOp = DeliveryOperation.pending(
                ORDER_ID, DeliveryOperationType.RELEASE, USER_ID, Role.REPARTIDOR,
                "Pinchadura", null, DELIVERY_ID, OrderStatus.LISTO_PARA_RETIRO, "corr-1", NOW)
                .withId(101L);
        when(deliveryOperationRepository.create(any(DeliveryOperation.class))).thenReturn(pendingOp);

        ExternalServiceException networkError = new ExternalServiceException(HttpStatus.SERVICE_UNAVAILABLE, "Delivery caido");
        org.mockito.Mockito.doThrow(networkError).when(deliveryPort).releaseRoute(ORDER_ID, DELIVERY_ID);

        assertThrows(ExternalServiceException.class,
                () -> useCase.execute(ORDER_ID, USER_ID, "Pinchadura", "corr-1"));

        assertThat(order.getDeliveryId()).isEqualTo(DELIVERY_ID);
        verify(orderRepository, never()).save(order);
        // La operacion NO se marca fallida: queda PENDING para recovery
        verify(deliveryOperationRepository, never()).save(any());
    }

    @Test
    void llamadaRepetidaCuandoYaFueLiberadaDevuelveExitoIdempotente() {
        Order alreadyReleasedOrder = buildOrder(OrderStatus.LISTO_PARA_RETIRO, null);
        when(deliveryPort.findDeliveryIdByUserId(USER_ID)).thenReturn(Optional.of(DELIVERY_ID));
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(alreadyReleasedOrder));

        DeliveryOperation pastOp = DeliveryOperation.pending(
                ORDER_ID, DeliveryOperationType.RELEASE, USER_ID, Role.REPARTIDOR,
                "Pinchadura", null, DELIVERY_ID, OrderStatus.LISTO_PARA_RETIRO, "corr-0", NOW)
                .withId(99L)
                .succeed(null, OrderStatus.LISTO_PARA_RETIRO, NOW);
        when(deliveryOperationRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(pastOp));

        useCase.execute(ORDER_ID, USER_ID, "Pinchadura", "corr-1");

        verify(deliveryPort, never()).releaseRoute(any(), any());
        verify(deliveryOperationRepository, never()).create(any());
        verify(orderRepository, never()).save(any());
    }

    private static Order buildOrder(OrderStatus status, UUID deliveryId) {
        return Order.builder()
                .id(ORDER_ID)
                .clientId(UUID.randomUUID())
                .restaurantId(UUID.randomUUID())
                .deliveryId(deliveryId)
                .status(status)
                .address("Calle Falsa 123")
                .subtotal(BigDecimal.valueOf(10000))
                .deliveryFee(BigDecimal.valueOf(1500))
                .total(BigDecimal.valueOf(11500))
                .paymentMethod(PaymentMethod.EFECTIVO)
                .createdAt(NOW)
                .build();
    }
}
