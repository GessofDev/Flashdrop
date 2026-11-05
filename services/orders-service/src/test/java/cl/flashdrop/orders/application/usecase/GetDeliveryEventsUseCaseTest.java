package cl.flashdrop.orders.application.usecase;

import cl.flashdrop.orders.domain.model.DeliveryOperation;
import cl.flashdrop.orders.domain.model.DeliveryOperationState;
import cl.flashdrop.orders.domain.model.DeliveryOperationType;
import cl.flashdrop.orders.domain.model.OrderStatus;
import cl.flashdrop.orders.domain.model.Role;
import cl.flashdrop.orders.domain.port.DeliveryOperationRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetDeliveryEventsUseCaseTest {

    @Mock
    private DeliveryOperationRepositoryPort deliveryOperationRepository;

    @InjectMocks
    private GetDeliveryEventsUseCase useCase;

    private final UUID orderId = UUID.randomUUID();

    @Test
    @DisplayName("Retorna historial de operaciones en orden cronologico")
    void retornaHistorialCronologico() {
        DeliveryOperation op1 = DeliveryOperation.pending(
                orderId, DeliveryOperationType.RELEASE, UUID.randomUUID(), Role.REPARTIDOR,
                "Motivo 1", null, UUID.randomUUID(), OrderStatus.LISTO_PARA_RETIRO, "c1", OffsetDateTime.now().minusHours(2)
        ).withId(1L);

        DeliveryOperation op2 = DeliveryOperation.pending(
                orderId, DeliveryOperationType.CANCEL_AFTER_PICKUP, UUID.randomUUID(), Role.SOPORTE,
                "Motivo 2", OrderStatus.RECHAZADO, UUID.randomUUID(), OrderStatus.RETIRADO, "c2", OffsetDateTime.now().minusHours(1)
        ).withId(2L);

        when(deliveryOperationRepository.findByOrderId(orderId)).thenReturn(List.of(op1, op2));

        List<DeliveryOperation> result = useCase.execute(orderId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo(1L);
        assertThat(result.get(1).id()).isEqualTo(2L);
        verify(deliveryOperationRepository).findByOrderId(orderId);
    }
}
