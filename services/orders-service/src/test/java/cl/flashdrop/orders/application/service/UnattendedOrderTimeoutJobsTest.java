package cl.flashdrop.orders.application.service;

import cl.flashdrop.orders.domain.exception.OrderDomainException;
import cl.flashdrop.orders.domain.model.CancellationReason;
import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.model.OrderStatus;
import cl.flashdrop.orders.domain.port.OrderRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnattendedOrderTimeoutJobsTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-19T12:00:00Z");

    @Mock private OrderRepositoryPort orderRepository;
    @Mock private UnattendedOrderCanceller canceller;

    private UnattendedOrderTimeoutJobs jobs;

    @BeforeEach
    void setUp() {
        jobs = new UnattendedOrderTimeoutJobs(orderRepository, canceller, Clock.fixed(NOW.toInstant(), ZoneOffset.UTC));
    }

    private static Order order(OrderStatus status) {
        return Order.builder().id(UUID.randomUUID()).status(status).build();
    }

    @Test
    void sinRepartidorCancelaLosPedidosTomadosPorLaTiendaConLaEsperaVencida() {
        Order preparando = order(OrderStatus.PREPARANDO), listo = order(OrderStatus.LISTO_PARA_RETIRO);
        when(orderRepository.findCourierWaitOverdue(NOW, UnattendedOrderTimeoutJobs.BATCH_SIZE))
                .thenReturn(List.of(preparando, listo));
        when(canceller.cancel(any(), any())).thenReturn(true);

        jobs.cancelCourierWaitTimeouts();

        verify(canceller).cancel(preparando, CancellationReason.NO_COURIER);
        verify(canceller).cancel(listo, CancellationReason.NO_COURIER);
    }

    @Test
    void unFalloNoFrenaAlRestoDeLosPedidos() {
        Order bad = order(OrderStatus.PREPARANDO), good = order(OrderStatus.PREPARANDO), taken = order(OrderStatus.PREPARANDO);
        when(canceller.cancel(bad, CancellationReason.NO_COURIER)).thenThrow(new OrderDomainException("Delivery caido"));
        when(canceller.cancel(taken, CancellationReason.NO_COURIER)).thenReturn(false);
        when(canceller.cancel(good, CancellationReason.NO_COURIER)).thenReturn(true);

        // El pedido que ya tomo un repartidor devuelve false y no cuenta como cancelado.
        assertEquals(1, jobs.run(List.of(bad, taken, good), CancellationReason.NO_COURIER));
        verify(canceller).cancel(good, CancellationReason.NO_COURIER);
    }

    @Test
    void sinPedidosVencidosNoHaceNada() {
        when(orderRepository.findCourierWaitOverdue(NOW, UnattendedOrderTimeoutJobs.BATCH_SIZE)).thenReturn(List.of());

        jobs.cancelCourierWaitTimeouts();

        verify(canceller, never()).cancel(any(), any());
    }
}
