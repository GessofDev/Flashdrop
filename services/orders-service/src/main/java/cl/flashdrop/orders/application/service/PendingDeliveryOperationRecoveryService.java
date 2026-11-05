package cl.flashdrop.orders.application.service;

import cl.flashdrop.orders.application.usecase.CancelPickedUpOrderUseCase;
import cl.flashdrop.orders.application.usecase.ReleaseAssignedOrderUseCase;
import cl.flashdrop.orders.domain.model.DeliveryOperation;
import cl.flashdrop.orders.domain.model.DeliveryOperationState;
import cl.flashdrop.orders.domain.model.DeliveryOperationType;
import cl.flashdrop.orders.domain.port.DeliveryOperationRepositoryPort;
import cl.flashdrop.orders.domain.port.DeliveryPort;
import cl.flashdrop.orders.infrastructure.exception.ExternalServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PendingDeliveryOperationRecoveryService {

    private static final Duration PENDING_THRESHOLD = Duration.ofMinutes(2);
    private static final int BATCH_SIZE = 50;

    private final DeliveryOperationRepositoryPort deliveryOperationRepository;
    private final DeliveryPort deliveryPort;
    private final ReleaseAssignedOrderUseCase releaseAssignedOrderUseCase;
    private final CancelPickedUpOrderUseCase cancelPickedUpOrderUseCase;

    @Scheduled(fixedDelayString = "${flashdrop.orders.recovery.pending-operations-delay-ms:60000}")
    public void recoverPendingOperations() {
        List<DeliveryOperation> pendingOps = deliveryOperationRepository.findByState(DeliveryOperationState.PENDING, BATCH_SIZE);
        if (pendingOps.isEmpty()) {
            return;
        }

        OffsetDateTime cutoff = OffsetDateTime.now().minus(PENDING_THRESHOLD);
        for (DeliveryOperation op : pendingOps) {
            if (op.createdAt().isAfter(cutoff)) {
                // Operation is still within normal in-flight execution window
                continue;
            }
            recoverSingleOperation(op);
        }
    }

    public void recoverSingleOperation(DeliveryOperation op) {
        log.info("Iniciando recuperacion para operacion {} (orderId={}, tipo={})",
                op.id(), op.orderId(), op.operationType());

        if (op.operationType() == DeliveryOperationType.RELEASE) {
            try {
                deliveryPort.releaseRoute(op.orderId(), op.previousDeliveryId());
                releaseAssignedOrderUseCase.finalizeSuccess(op.orderId(), op.previousDeliveryId(), op.id());
            } catch (ExternalServiceException e) {
                if (e.getStatus() != null && e.getStatus().is4xxClientError()) {
                    releaseAssignedOrderUseCase.finalizeFailed(op.id(), e.getStatus().name());
                } else {
                    log.warn("Fallo transitorio al reintentar liberacion para operacion {}: {}", op.id(), e.getMessage());
                }
            } catch (Exception e) {
                log.error("Error inesperado en recuperacion de operacion {}: {}", op.id(), e.getMessage(), e);
            }
        } else if (op.operationType() == DeliveryOperationType.CANCEL_AFTER_PICKUP) {
            try {
                deliveryPort.closeRoute(op.orderId(), op.targetStatus().getValue());
                cancelPickedUpOrderUseCase.finalizeSuccess(op.orderId(), op.targetStatus(), op.id());
            } catch (ExternalServiceException e) {
                if (e.getStatus() != null && e.getStatus().is4xxClientError()) {
                    cancelPickedUpOrderUseCase.finalizeFailed(op.id(), e.getStatus().name());
                } else {
                    log.warn("Fallo transitorio al reintentar anulacion para operacion {}: {}", op.id(), e.getMessage());
                }
            } catch (Exception e) {
                log.error("Error inesperado en recuperacion de operacion {}: {}", op.id(), e.getMessage(), e);
            }
        }
    }
}
