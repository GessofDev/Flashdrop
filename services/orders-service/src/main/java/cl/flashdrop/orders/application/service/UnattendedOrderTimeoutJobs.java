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
 * Tareas programadas de F7: ningun pedido queda esperando para siempre.
 *
 * <ul>
 *   <li><b>Tienda sin respuesta:</b> un pedido pagado que sigue en "Nuevo pedido" pasado el plazo global de
 *       respuesta de la tienda se cancela.</li>
 *   <li><b>Sin repartidor:</b> un pedido aceptado por la tienda que no tiene repartidor pasado el plazo global de
 *       espera (contado desde que la tienda acepto) se cancela.</li>
 * </ul>
 *
 * <p>Solo se avisa al cliente cuando se cancela (el pedido pasa a "Cancelado" con su motivo). Las devoluciones
 * estan fuera de este alcance. Los pedidos anteriores a esta funcion no tienen plazo y nunca se cancelan solos.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnattendedOrderTimeoutJobs {

    static final int BATCH_SIZE = 50;

    private final OrderRepositoryPort orderRepository;
    private final UnattendedOrderCanceller canceller;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${flashdrop.orders.timeouts.store-response-delay-ms:60000}")
    public void cancelStoreResponseTimeouts() {
        run(orderRepository.findStoreResponseOverdue(OffsetDateTime.now(clock), BATCH_SIZE),
                CancellationReason.STORE_NO_RESPONSE);
    }

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
                log.error("No se pudo cancelar el pedido {} por {}; se reintenta en el siguiente ciclo",
                        order.getId(), reason, e);
            }
        }
        return cancelled;
    }
}
