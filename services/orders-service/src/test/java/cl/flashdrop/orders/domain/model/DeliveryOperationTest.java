package cl.flashdrop.orders.domain.model;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeliveryOperationTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-05T12:00:00Z");
    private static final UUID ORDER_ID = UUID.fromString("00000000-0000-0000-0000-000000000065");
    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID DELIVERY_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    void unaOperacionDeLiberacionComienzaPendienteYConservaLaTrazabilidad() {
        DeliveryOperation operation = pendingRelease();

        assertEquals(DeliveryOperationState.PENDING, operation.state());
        assertEquals(DeliveryOperationType.RELEASE, operation.operationType());
        assertEquals("No puedo retirarme en tiempo", operation.reason());
        assertEquals(Role.REPARTIDOR, operation.actorRole());
        assertEquals(ACTOR_ID, operation.actorUserId());
        assertEquals(DELIVERY_ID, operation.previousDeliveryId());
        assertEquals(OrderStatus.LISTO_PARA_RETIRO, operation.previousStatus());
        assertNull(operation.resultingDeliveryId());
        assertNull(operation.failureCode());
    }

    @Test
    void unaOperacionPendientePuedeCompletarseSinPerderLaAuditoriaPrevia() {
        DeliveryOperation operation = pendingRelease().succeed(
                null, OrderStatus.LISTO_PARA_RETIRO, NOW.plusMinutes(2));

        assertEquals(DeliveryOperationState.SUCCEEDED, operation.state());
        assertEquals(OrderStatus.LISTO_PARA_RETIRO, operation.resultingStatus());
        assertEquals(NOW.plusMinutes(2), operation.updatedAt());
        assertEquals("No puedo retirarme en tiempo", operation.reason());
    }

    @Test
    void unaOperacionPendientePuedeFallarYGuardarElCodigo() {
        DeliveryOperation operation = pendingRelease().fail(
                "DELIVERY_UNAVAILABLE", NOW.plusMinutes(2));

        assertEquals(DeliveryOperationState.FAILED, operation.state());
        assertEquals("DELIVERY_UNAVAILABLE", operation.failureCode());
        assertNull(operation.resultingStatus());
    }

    @Test
    void unaOperacionTerminalNoCambiaDeEstadoDosVeces() {
        DeliveryOperation completed = pendingRelease().succeed(
                null, OrderStatus.LISTO_PARA_RETIRO, NOW.plusMinutes(2));

        assertThrows(IllegalStateException.class, () -> completed.fail(
                "LATE_FAILURE", NOW.plusMinutes(3)));
        assertThrows(IllegalStateException.class, () -> completed.succeed(
                null, OrderStatus.LISTO_PARA_RETIRO, NOW.plusMinutes(3)));
    }

    @Test
    void elMotivoEsObligatorioYEstaAcotado() {
        assertThrows(IllegalArgumentException.class, () -> DeliveryOperation.pending(
                ORDER_ID,
                DeliveryOperationType.RELEASE,
                ACTOR_ID,
                Role.REPARTIDOR,
                "   ",
                null,
                DELIVERY_ID,
                OrderStatus.LISTO_PARA_RETIRO,
                "corr-1",
                NOW));

        assertThrows(IllegalArgumentException.class, () -> DeliveryOperation.pending(
                ORDER_ID,
                DeliveryOperationType.RELEASE,
                ACTOR_ID,
                Role.REPARTIDOR,
                "x".repeat(501),
                null,
                DELIVERY_ID,
                OrderStatus.LISTO_PARA_RETIRO,
                "corr-1",
                NOW));
    }

    @Test
    void laAnulacionExigeUnEstadoFinalCanceladoORechazado() {
        assertThrows(IllegalArgumentException.class, () -> DeliveryOperation.pending(
                ORDER_ID,
                DeliveryOperationType.CANCEL_AFTER_PICKUP,
                ACTOR_ID,
                Role.SOPORTE,
                "Incidente",
                null,
                DELIVERY_ID,
                OrderStatus.RETIRADO,
                "corr-2",
                NOW));

        assertThrows(IllegalArgumentException.class, () -> DeliveryOperation.pending(
                ORDER_ID,
                DeliveryOperationType.CANCEL_AFTER_PICKUP,
                ACTOR_ID,
                Role.SOPORTE,
                "Incidente",
                OrderStatus.RETIRADO,
                DELIVERY_ID,
                OrderStatus.RETIRADO,
                "corr-2",
                NOW));
    }

    private static DeliveryOperation pendingRelease() {
        return DeliveryOperation.pending(
                ORDER_ID,
                DeliveryOperationType.RELEASE,
                ACTOR_ID,
                Role.REPARTIDOR,
                "No puedo retirarme en tiempo",
                null,
                DELIVERY_ID,
                OrderStatus.LISTO_PARA_RETIRO,
                "corr-1",
                NOW);
    }
}
