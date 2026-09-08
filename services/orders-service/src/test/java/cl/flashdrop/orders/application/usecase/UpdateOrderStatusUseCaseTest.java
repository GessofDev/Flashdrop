package cl.flashdrop.orders.application.usecase;

import cl.flashdrop.orders.domain.exception.OrderDomainException;
import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.model.OrderStatus;
import cl.flashdrop.orders.domain.port.DeliveryPort;
import cl.flashdrop.orders.domain.port.EventPublisherPort;
import cl.flashdrop.orders.domain.port.OrderRepositoryPort;
import cl.flashdrop.orders.infrastructure.messaging.event.OrderStatusUpdatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cubre ORD-F8: sin tests dedicados previamente.
 */
@ExtendWith(MockitoExtension.class)
class UpdateOrderStatusUseCaseTest {

    @Mock
    private OrderRepositoryPort orderRepository;

    @Mock
    private DeliveryPort deliveryPort;

    @Mock
    private EventPublisherPort eventPublisher;

    private UpdateOrderStatusUseCase useCase;

    private static final UUID ORDER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new UpdateOrderStatusUseCase(orderRepository, deliveryPort, eventPublisher);
        ReflectionTestUtils.setField(useCase, "statusUpdatedRoutingKey", "order.status.updated");
    }

    private Order orderWithStatus(OrderStatus status) {
        return Order.builder().id(ORDER_ID).status(status).build();
    }

    @Test
    void transicionValida_persisteSincronizaRutaYPublicaEvento() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(orderWithStatus(OrderStatus.NUEVO_PEDIDO)));

        useCase.execute(ORDER_ID, "Preparando");

        verify(orderRepository).updateStatus(ORDER_ID, OrderStatus.PREPARANDO);
        verify(deliveryPort).updateRouteStatusByOrder(ORDER_ID, "Preparando");

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publish(eq("order.status.updated"), eventCaptor.capture());
        OrderStatusUpdatedEvent event = (OrderStatusUpdatedEvent) eventCaptor.getValue();
        assertEquals(ORDER_ID, event.getOrderId());
        assertEquals("Nuevo pedido", event.getPreviousStatus());
        assertEquals("Preparando", event.getNewStatus());
    }

    @Test
    void estadoInvalido_lanzaExcepcionSinTocarRepositorio() {
        OrderDomainException ex = assertThrows(OrderDomainException.class,
                () -> useCase.execute(ORDER_ID, "Estado inexistente"));

        assertEquals("Estado no valido: Estado inexistente", ex.getMessage());
        verify(orderRepository, never()).findById(any());
        verify(orderRepository, never()).updateStatus(any(), any());
    }

    @Test
    void pedidoNoEncontrado_lanzaExcepcion() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        OrderDomainException ex = assertThrows(OrderDomainException.class,
                () -> useCase.execute(ORDER_ID, "Preparando"));

        assertEquals("Pedido no encontrado", ex.getMessage());
        verify(orderRepository, never()).updateStatus(any(), any());
    }

    @Test
    void transicionDesdeEntregado_lanzaExcepcionYNoPublicaEvento() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(orderWithStatus(OrderStatus.ENTREGADO)));

        assertThrows(OrderDomainException.class, () -> useCase.execute(ORDER_ID, "Preparando"));

        verify(orderRepository, never()).updateStatus(any(), any());
        verify(deliveryPort, never()).updateRouteStatusByOrder(any(), anyString());
        verify(eventPublisher, never()).publish(anyString(), any());
    }
}
