package cl.flashdrop.orders.application.service;

import cl.flashdrop.orders.domain.model.CancellationReason;
import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.port.OrderRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Tarea programada de F7: ningun pedido queda tomado para siempre sin repartidor.
 *
 * <p>La tienda toma todo pedido pagado (pasa directo a Preparando), asi que ya no hay espera de aceptacion. Un
 * pedido tomado por la tienda que sigue sin repartidor pasada la espera global (contada desde que el pedido
 * entro, es decir, desde el pago) se cancela.</p>
 *
 * <p>Solo se avisa al cliente cuando se cancela (el pedido pasa a "Cancelado" con su motivo). Las devoluciones
 * estan fuera de este alcance. Los pedidos anteriores a esta funcion no tienen plazo y nunca se cancelan solos.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnattendedOrderTimeoutJobs {

    static final int BATCH_SIZE = 200;

    private final OrderRepositoryPort orderRepository;
    private final UnattendedOrderCanceller canceller;
    private final Clock clock;


    @Scheduled(fixedDelayString = "${flashdrop.orders.timeouts.courier-wait-delay-ms:60000}")
    public void cancelCourierWaitTimeouts() {
        run(orderRepository.findCourierWaitOverdue(OffsetDateTime.now(clock), BATCH_SIZE),
                CancellationReason.NO_COURIER);
    }

    /** @return cuantos pedidos se cancelaron. Cada uno en su transaccion: un fallo no frena a los demas. */
    int run(List<Order> overdue, CancellationReason reason) {
        int cancelled = 0;
        for (Order order : overdue) {
            try {
                if (canceller.cancel(order, reason)) {
                    cancelled++;
                }
            } catch (RuntimeException e) {
                log.error("No se pudo cancelar el pedido {} por {} ({}); se reintenta en el siguiente ciclo",
                        order.getId(), reason, e.toString());
            }
        }
        return cancelled;
    }
}
