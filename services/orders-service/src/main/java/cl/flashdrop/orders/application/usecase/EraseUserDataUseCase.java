package cl.flashdrop.orders.application.usecase;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import cl.flashdrop.orders.domain.exception.AccountDeletionBlockedException;
import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.port.ClientPort;
import cl.flashdrop.orders.domain.port.DeliveryOperationRepositoryPort;
import cl.flashdrop.orders.domain.port.DeliveryPort;
import cl.flashdrop.orders.domain.port.OrderRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Eliminacion de cuenta (F5): borra los datos personales que Orders guarda de esa persona.
 *
 * <p>No cancela nada: si hay pedidos en curso responde {@link AccountDeletionBlockedException} (409) y no
 * toca nada. Los pedidos terminados se conservan como evidencia minima (id, estado, montos, fechas, tienda
 * y productos) sin ningun dato del cliente. Todos los pasos son idempotentes y el vinculo del perfil con el
 * usuario se borra al final, para que un reintento tras un fallo parcial siga encontrando los pedidos.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EraseUserDataUseCase {

    public record Result(int ordersAnonymized, int routesAnonymized, int operationsAnonymized, boolean profileErased) {
    }

    private final GetAccountDeletionBlockersUseCase blockersUseCase;
    private final OrderRepositoryPort orderRepository;
    private final ClientPort clientPort;
    private final DeliveryPort deliveryPort;
    private final DeliveryOperationRepositoryPort deliveryOperationRepository;

    public Result execute(UUID userId) {
        GetAccountDeletionBlockersUseCase.Blockers blockers = blockersUseCase.execute(userId);
        if (blockers.total() > 0) {
            throw new AccountDeletionBlockedException(
                    "Tienes pedidos en curso (como cliente: " + blockers.asClient()
                            + ", como tienda: " + blockers.asRestaurant()
                            + ", como repartidor: " + blockers.asCourier()
                            + "). Cancelalos o espera a que terminen antes de eliminar tu cuenta.");
        }

        int routes = 0;
        int orders = 0;
        UUID clientId = clientPort.findClientIdByUserId(userId).orElse(null);
        if (clientId != null) {
            List<UUID> orderIds = orderRepository.findByClientId(clientId).stream().map(Order::getId).toList();
            routes = deliveryPort.anonymizeClientRoutes(orderIds);
            orders = orderRepository.anonymizeAddressesByClient(clientId);
        }
        int operations = deliveryOperationRepository.anonymizeActor(userId);
        boolean profileErased = clientPort.anonymizeByUserId(userId);

        log.info("Datos de usuario eliminados en Orders: pedidos={}, rutas={}, operaciones={}, perfil={}",
                orders, routes, operations, profileErased);
        return new Result(orders, routes, operations, profileErased);
    }
}
