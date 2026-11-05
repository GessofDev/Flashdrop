package cl.flashdrop.orders.application.usecase;

import cl.flashdrop.orders.domain.exception.ForbiddenOperationException;
import cl.flashdrop.orders.domain.exception.OrderDomainException;
import cl.flashdrop.orders.domain.model.*;
import cl.flashdrop.orders.domain.port.DeliveryOperationRepositoryPort;
import cl.flashdrop.orders.domain.port.DeliveryPort;
import cl.flashdrop.orders.domain.port.OrderRepositoryPort;
import cl.flashdrop.orders.infrastructure.exception.ExternalServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CancelPickedUpOrderUseCaseTest {

    @Mock
    private OrderRepositoryPort orderRepository;

    @Mock
    private DeliveryPort deliveryPort;

    @Mock
    private DeliveryOperationRepositoryPort deliveryOperationRepository;

    @InjectMocks
    private CancelPickedUpOrderUseCase useCase;

    private final UUID orderId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID deliveryId = UUID.randomUUID();
    private final UUID otherDeliveryId = UUID.randomUUID();

    private Order orderRetirado;
    private Order orderEnCamino;
    private Order orderListoParaRetiro;
    private Order orderEntregado;

    @BeforeEach
    void setUp() {
        orderRetirado = Order.builder()
                .id(orderId)
                .status(OrderStatus.RETIRADO)
                .deliveryId(deliveryId)
                .total(new BigDecimal("15000"))
                .build();

        orderEnCamino = Order.builder()
                .id(orderId)
                .status(OrderStatus.EN_CAMINO)
                .deliveryId(deliveryId)
                .total(new BigDecimal("15000"))
                .build();

        orderListoParaRetiro = Order.builder()
                .id(orderId)
                .status(OrderStatus.LISTO_PARA_RETIRO)
                .deliveryId(deliveryId)
                .total(new BigDecimal("15000"))
                .build();

        orderEntregado = Order.builder()
                .id(orderId)
                .status(OrderStatus.ENTREGADO)
                .deliveryId(deliveryId)
                .total(new BigDecimal("15000"))
                .build();
    }

    @Test
    @DisplayName("Repartidor anula pedido RETIRADO exitosamente a CANCELADO")
    void repartidorCancelaPedidoRetiradoExitosamente() {
        when(deliveryPort.findDeliveryIdByUserId(userId)).thenReturn(Optional.of(deliveryId));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(orderRetirado));

        DeliveryOperation pendingOp = DeliveryOperation.pending(
                orderId, DeliveryOperationType.CANCEL_AFTER_PICKUP, userId, Role.REPARTIDOR,
                "Problema de entrega", OrderStatus.CANCELADO, deliveryId, OrderStatus.RETIRADO,
                "corr-1", OffsetDateTime.now()
        ).withId(10L);

        when(deliveryOperationRepository.create(any())).thenReturn(pendingOp);
        when(deliveryOperationRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pendingOp));

        useCase.execute(orderId, userId, Role.REPARTIDOR, OrderStatus.CANCELADO, "Problema de entrega", "corr-1");

        verify(deliveryPort).closeRoute(orderId, OrderStatus.CANCELADO.getValue());
        verify(orderRepository).save(argThat(saved ->
                saved.getStatus() == OrderStatus.CANCELADO && deliveryId.equals(saved.getDeliveryId())));
        verify(deliveryOperationRepository).save(argThat(saved ->
                saved.state() == DeliveryOperationState.SUCCEEDED && saved.resultingStatus() == OrderStatus.CANCELADO));
    }

    @Test
    @DisplayName("Soporte anula pedido EN_CAMINO legacy exitosamente a RECHAZADO")
    void soporteRechazaPedidoEnCaminoExitosamente() {
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(orderEnCamino));

        DeliveryOperation pendingOp = DeliveryOperation.pending(
                orderId, DeliveryOperationType.CANCEL_AFTER_PICKUP, userId, Role.SOPORTE,
                "Fraude detectado", OrderStatus.RECHAZADO, deliveryId, OrderStatus.EN_CAMINO,
                "corr-2", OffsetDateTime.now()
        ).withId(20L);

        when(deliveryOperationRepository.create(any())).thenReturn(pendingOp);
        when(deliveryOperationRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(pendingOp));

        useCase.execute(orderId, userId, Role.SOPORTE, OrderStatus.RECHAZADO, "Fraude detectado", "corr-2");

        // Soporte doesn't require courier profile check
        verify(deliveryPort, never()).findDeliveryIdByUserId(any());
        verify(deliveryPort).closeRoute(orderId, OrderStatus.RECHAZADO.getValue());
        verify(orderRepository).save(argThat(saved ->
                saved.getStatus() == OrderStatus.RECHAZADO && deliveryId.equals(saved.getDeliveryId())));
    }

    @Test
    @DisplayName("Repartidor no asignado produce 403 ForbiddenOperationException")
    void repartidorNoAsignadoLanzaForbidden() {
        when(deliveryPort.findDeliveryIdByUserId(userId)).thenReturn(Optional.of(otherDeliveryId));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(orderRetirado));

        assertThatThrownBy(() -> useCase.execute(orderId, userId, Role.REPARTIDOR, OrderStatus.CANCELADO, "Motivo", "c1"))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("No puedes anular un pedido asignado a otro repartidor");

        verify(deliveryPort, never()).closeRoute(any(), any());
    }

    @Test
    @DisplayName("Estado previo inválido (ej: LISTO_PARA_RETIRO) produce OrderDomainException")
    void estadoPrevioInvalidoLanzaException() {
        when(deliveryPort.findDeliveryIdByUserId(userId)).thenReturn(Optional.of(deliveryId));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(orderListoParaRetiro));

        assertThatThrownBy(() -> useCase.execute(orderId, userId, Role.REPARTIDOR, OrderStatus.CANCELADO, "Motivo", "c1"))
                .isInstanceOf(OrderDomainException.class)
                .hasMessageContaining("Solo se pueden anular pedidos retirados");

        verify(deliveryPort, never()).closeRoute(any(), any());
    }

    @Test
    @DisplayName("Estado terminal (ej: ENTREGADO) produce OrderDomainException")
    void estadoTerminalLanzaException() {
        when(deliveryPort.findDeliveryIdByUserId(userId)).thenReturn(Optional.of(deliveryId));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(orderEntregado));

        assertThatThrownBy(() -> useCase.execute(orderId, userId, Role.REPARTIDOR, OrderStatus.CANCELADO, "Motivo", "c1"))
                .isInstanceOf(OrderDomainException.class)
                .hasMessageContaining("El pedido ya está en estado terminal");

        verify(deliveryPort, never()).closeRoute(any(), any());
    }

    @Test
    @DisplayName("Falla 4xx de Delivery marca la operación como FAILED y relanza excepción")
    void falla4xxDeliveryMarcaFailed() {
        when(deliveryPort.findDeliveryIdByUserId(userId)).thenReturn(Optional.of(deliveryId));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(orderRetirado));

        DeliveryOperation pendingOp = DeliveryOperation.pending(
                orderId, DeliveryOperationType.CANCEL_AFTER_PICKUP, userId, Role.REPARTIDOR,
                "Problema de entrega", OrderStatus.CANCELADO, deliveryId, OrderStatus.RETIRADO,
                "corr-1", OffsetDateTime.now()
        ).withId(30L);

        when(deliveryOperationRepository.create(any())).thenReturn(pendingOp);
        when(deliveryOperationRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(pendingOp));

        doThrow(new ExternalServiceException(HttpStatus.CONFLICT, "Conflicto en ruta"))
                .when(deliveryPort).closeRoute(orderId, OrderStatus.CANCELADO.getValue());

        assertThatThrownBy(() -> useCase.execute(orderId, userId, Role.REPARTIDOR, OrderStatus.CANCELADO, "Problema de entrega", "corr-1"))
                .isInstanceOf(ExternalServiceException.class);

        verify(deliveryOperationRepository).save(argThat(saved ->
                saved.state() == DeliveryOperationState.FAILED && "CONFLICT".equals(saved.failureCode())));
        // Order status must NOT change
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Idempotencia: pedido ya cancelado con operación previa devuelve sin error ni nueva llamada a Delivery")
    void idempotenciaMismoEstadoNoReitera() {
        Order orderYaCancelado = Order.builder()
                .id(orderId)
                .status(OrderStatus.CANCELADO)
                .deliveryId(deliveryId)
                .build();

        DeliveryOperation opPrevia = DeliveryOperation.pending(
                orderId, DeliveryOperationType.CANCEL_AFTER_PICKUP, userId, Role.REPARTIDOR,
                "Cancelado", OrderStatus.CANCELADO, deliveryId, OrderStatus.RETIRADO, "c", OffsetDateTime.now()
        ).succeed(deliveryId, OrderStatus.CANCELADO, OffsetDateTime.now());

        when(deliveryPort.findDeliveryIdByUserId(userId)).thenReturn(Optional.of(deliveryId));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(orderYaCancelado));
        when(deliveryOperationRepository.findByOrderId(orderId)).thenReturn(List.of(opPrevia));

        useCase.execute(orderId, userId, Role.REPARTIDOR, OrderStatus.CANCELADO, "Cancelado", "c");

        verify(deliveryPort, never()).closeRoute(any(), any());
        verify(deliveryOperationRepository, never()).create(any());
    }
}
