package cl.flashdrop.orders.application.usecase;

import cl.flashdrop.orders.domain.model.DeliveryOperation;
import cl.flashdrop.orders.domain.port.DeliveryOperationRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetDeliveryEventsUseCase {

    private final DeliveryOperationRepositoryPort deliveryOperationRepository;

    @Transactional(readOnly = true)
    public List<DeliveryOperation> execute(UUID orderId) {
        return deliveryOperationRepository.findByOrderId(orderId);
    }
}
