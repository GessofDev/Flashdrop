package cl.flashdrop.orders.application.usecase;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.flashdrop.orders.domain.port.CatalogPort;
import cl.flashdrop.orders.domain.port.ClientPort;
import cl.flashdrop.orders.domain.port.DeliveryPort;
import cl.flashdrop.orders.domain.port.OrderRepositoryPort;
import lombok.RequiredArgsConstructor;

/**
 * Eliminacion de cuenta (F5): cuenta los pedidos en curso de la persona en cada uno de sus papeles.
 * No se cancela nada automaticamente: mientras haya pedidos en curso la cuenta no se puede eliminar y la
 * persona debe cancelarlos o cerrarlos por el flujo normal. Si Catalog o Delivery no responden la excepcion
 * se propaga (503): nunca se informa "sin bloqueos" por no haber podido comprobarlo.
 */
@Service
@RequiredArgsConstructor
public class GetAccountDeletionBlockersUseCase {

    public record Blockers(int asClient, int asRestaurant, int asCourier) {
        public int total() {
            return asClient + asRestaurant + asCourier;
        }
    }

    private final OrderRepositoryPort orderRepository;
    private final ClientPort clientPort;
    private final CatalogPort catalogPort;
    private final DeliveryPort deliveryPort;

    @Transactional(readOnly = true)
    public Blockers execute(UUID userId) {
        int asClient = clientPort.findClientIdByUserId(userId).map(orderRepository::countActiveByClient).orElse(0);
        int asRestaurant = catalogPort.findRestaurantIdByUserId(userId)
                .map(orderRepository::countActiveByRestaurant).orElse(0);
        int asCourier = deliveryPort.findDeliveryIdByUserId(userId)
                .map(orderRepository::countActiveByDeliveryPerson).orElse(0);
        return new Blockers(asClient, asRestaurant, asCourier);
    }
}
