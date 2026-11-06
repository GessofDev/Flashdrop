package cl.flashdrop.orders.domain.port;

import cl.flashdrop.orders.domain.model.DeliveryOperation;
import cl.flashdrop.orders.domain.model.DeliveryOperationState;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeliveryOperationRepositoryPort {

    DeliveryOperation create(DeliveryOperation operation);

    Optional<DeliveryOperation> findByIdForUpdate(Long id);

    List<DeliveryOperation> findByOrderId(UUID orderId);

    DeliveryOperation save(DeliveryOperation operation);

    List<DeliveryOperation> findByState(DeliveryOperationState state, int limit);

    /** Eliminacion de cuenta: borra la identidad y el motivo (texto libre) de las operaciones de ese actor. */
    int anonymizeActor(UUID userId);
}
