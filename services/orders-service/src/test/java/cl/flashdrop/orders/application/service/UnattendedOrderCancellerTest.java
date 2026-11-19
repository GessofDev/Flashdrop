package cl.flashdrop.orders.application.service;

import cl.flashdrop.orders.domain.model.CancellationReason;
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

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UnattendedOrderCancellerTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-19T12:00:00Z");

    @Mock private OrderRepositoryPort orderRepository;
    @Mock private DeliveryPort deliveryPort;
    @Mock private EventPublisherPort eventPublisher;

    private UnattendedOrderCanceller canceller;
    private final Order order = Order.builder().id(UUID.randomUUID()).status(OrderStatus.PREPARANDO).build();

    @BeforeEach
    void setUp() {
        canceller = new UnattendedOrderCanceller(orderRepository, deliveryPort, eventPublisher,
                Clock.fixed(NOW.toInstant(), ZoneOffset.UTC));
        ReflectionTestUtils.setField(canceller, "statusUpdatedRoutingKey", "order.status.updated");
    }

    @Test
    void cancelaCierraLaRutaYPublicaElEventoConElMotivo() {
        when(orderRepository.cancelUnattended(order.getId(), OrderStatus.PREPARANDO, CancellationReason.NO_COURIER, NOW))
                .thenReturn(true);

        assertTrue(canceller.cancel(order, CancellationReason.NO_COURIER));

        verify(deliveryPort).closeRoute(order.getId(), "Cancelado");
        ArgumentCaptor<OrderStatusUpdatedEvent> event = ArgumentCaptor.forClass(OrderStatusUpdatedEvent.class);
        verify(eventPublisher).publish(eq("order.status.updated"), event.capture());
        assertEquals("Preparando", event.getValue().getPreviousStatus());
        assertEquals("Cancelado", event.getValue().getNewStatus());
        assertEquals("NO_COURIER", event.getValue().getReason());
    }

    @Test
    void siElPedidoCambioOYaTieneRepartidorNoSeCancelaNiSeTocaDelivery() {
        when(orderRepository.cancelUnattended(any(), any(), any(), any())).thenReturn(false);

        assertFalse(canceller.cancel(order, CancellationReason.NO_COURIER));

        verifyNoInteractions(deliveryPort, eventPublisher);
    }

    @Test
    void siDeliveryFallaLaExcepcionSubeParaDeshacerLaCancelacionYReintentar() {
        when(orderRepository.cancelUnattended(any(), any(), any(), any())).thenReturn(true);
        doThrow(new IllegalStateException("delivery caido")).when(deliveryPort).closeRoute(any(), any());

        assertThrows(IllegalStateException.class, () -> canceller.cancel(order, CancellationReason.NO_COURIER));

        verifyNoInteractions(eventPublisher);
    }
}
