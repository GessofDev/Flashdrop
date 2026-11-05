package cl.flashdrop.orders.application.service;

import cl.flashdrop.orders.application.usecase.CancelPickedUpOrderUseCase;
import cl.flashdrop.orders.application.usecase.ReleaseAssignedOrderUseCase;
import cl.flashdrop.orders.domain.model.DeliveryOperation;
import cl.flashdrop.orders.domain.model.DeliveryOperationState;
import cl.flashdrop.orders.domain.model.DeliveryOperationType;
import cl.flashdrop.orders.domain.model.OrderStatus;
import cl.flashdrop.orders.domain.model.Role;
import cl.flashdrop.orders.domain.port.DeliveryOperationRepositoryPort;
import cl.flashdrop.orders.domain.port.DeliveryPort;
import cl.flashdrop.orders.infrastructure.exception.ExternalServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PendingDeliveryOperationRecoveryServiceTest {

    @Mock
    private DeliveryOperationRepositoryPort deliveryOperationRepository;

    @Mock
    private DeliveryPort deliveryPort;

    @Mock
    private ReleaseAssignedOrderUseCase releaseAssignedOrderUseCase;

    @Mock
    private CancelPickedUpOrderUseCase cancelPickedUpOrderUseCase;

    @InjectMocks
    private PendingDeliveryOperationRecoveryService recoveryService;

    private final UUID orderId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID deliveryId = UUID.randomUUID();

    @Test
    @DisplayName("Recupera operacion RELEASE pendiente antigua exitosamente")
    void recuperaReleasePendienteExitosamente() {
        OffsetDateTime oldDate = OffsetDateTime.now().minusMinutes(5);
        DeliveryOperation pendingRelease = DeliveryOperation.pending(
                orderId, DeliveryOperationType.RELEASE, userId, Role.REPARTIDOR,
                "Motivo", null, deliveryId, OrderStatus.LISTO_PARA_RETIRO, "c1", oldDate
        ).withId(101L);

        when(deliveryOperationRepository.findByState(eq(DeliveryOperationState.PENDING), anyInt()))
                .thenReturn(List.of(pendingRelease));

        recoveryService.recoverPendingOperations();

        verify(deliveryPort).releaseRoute(orderId, deliveryId);
        verify(releaseAssignedOrderUseCase).finalizeSuccess(orderId, deliveryId, 101L);
    }

    @Test
    @DisplayName("Recupera operacion CANCEL_AFTER_PICKUP pendiente antigua exitosamente")
    void recuperaCancelacionPendienteExitosamente() {
        OffsetDateTime oldDate = OffsetDateTime.now().minusMinutes(5);
        DeliveryOperation pendingCancel = DeliveryOperation.pending(
                orderId, DeliveryOperationType.CANCEL_AFTER_PICKUP, userId, Role.REPARTIDOR,
                "Motivo", OrderStatus.CANCELADO, deliveryId, OrderStatus.RETIRADO, "c2", oldDate
        ).withId(102L);

        when(deliveryOperationRepository.findByState(eq(DeliveryOperationState.PENDING), anyInt()))
                .thenReturn(List.of(pendingCancel));

        recoveryService.recoverPendingOperations();

        verify(deliveryPort).closeRoute(orderId, OrderStatus.CANCELADO.getValue());
        verify(cancelPickedUpOrderUseCase).finalizeSuccess(orderId, OrderStatus.CANCELADO, 102L);
    }

    @Test
    @DisplayName("Ignora operaciones pendientes recientes dentro del umbral de ejecucion en curso")
    void ignoraPendientesRecientes() {
        OffsetDateTime recentDate = OffsetDateTime.now().minusSeconds(10);
        DeliveryOperation recentPending = DeliveryOperation.pending(
                orderId, DeliveryOperationType.RELEASE, userId, Role.REPARTIDOR,
                "Motivo", null, deliveryId, OrderStatus.LISTO_PARA_RETIRO, "c3", recentDate
        ).withId(103L);

        when(deliveryOperationRepository.findByState(eq(DeliveryOperationState.PENDING), anyInt()))
                .thenReturn(List.of(recentPending));

        recoveryService.recoverPendingOperations();

        verify(deliveryPort, never()).releaseRoute(any(), any());
        verify(deliveryPort, never()).closeRoute(any(), any());
    }

    @Test
    @DisplayName("Falla 4xx en reintento marca operacion como FAILED")
    void falla4xxMarcaFailed() {
        OffsetDateTime oldDate = OffsetDateTime.now().minusMinutes(5);
        DeliveryOperation pendingRelease = DeliveryOperation.pending(
                orderId, DeliveryOperationType.RELEASE, userId, Role.REPARTIDOR,
                "Motivo", null, deliveryId, OrderStatus.LISTO_PARA_RETIRO, "c4", oldDate
        ).withId(104L);

        when(deliveryOperationRepository.findByState(eq(DeliveryOperationState.PENDING), anyInt()))
                .thenReturn(List.of(pendingRelease));

        doThrow(new ExternalServiceException(HttpStatus.CONFLICT, "Conflicto"))
                .when(deliveryPort).releaseRoute(orderId, deliveryId);

        recoveryService.recoverPendingOperations();

        verify(releaseAssignedOrderUseCase).finalizeFailed(104L, "CONFLICT");
    }
}
