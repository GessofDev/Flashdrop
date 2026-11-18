package com.flashdrop.reviews.domain.model;

import com.flashdrop.reviews.domain.valueobjects.RatingTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OrderSnapshot: la vista de la orden que reviews-service recibe de
 * orders-service por el endpoint interno. Es la única fuente de verdad para
 * el estado de la orden y para derivar el destino de la calificación (S7).
 */
@DisplayName("OrderSnapshot — estado y participantes de la orden")
class OrderSnapshotTest {

    private static OrderSnapshot delivered() {
        return new OrderSnapshot(501L, "ENTREGADO", 10L, 7L, 42L);
    }

    @Test
    @DisplayName("TC1: una orden ENTREGADO está entregada")
    void deliveredStatus_isDelivered() {
        assertThat(delivered().isDelivered()).isTrue();
    }

    @Test
    @DisplayName("TC2: cualquier otro estado no está entregada")
    void otherStatuses_areNotDelivered() {
        assertThat(new OrderSnapshot(501L, "CANCELADO", 10L, 7L, 42L).isDelivered()).isFalse();
        assertThat(new OrderSnapshot(501L, "NUEVO_PEDIDO", 10L, 7L, 42L).isDelivered()).isFalse();
        assertThat(new OrderSnapshot(501L, "RETIRADO", 10L, 7L, 42L).isDelivered()).isFalse();
        assertThat(new OrderSnapshot(501L, null, 10L, 7L, 42L).isDelivered()).isFalse();
    }

    @Test
    @DisplayName("TC3: el estado se compara sin distinguir mayúsculas ni espacios")
    void deliveredStatus_isCaseInsensitive() {
        assertThat(new OrderSnapshot(501L, "Entregado", 10L, 7L, 42L).isDelivered()).isTrue();
        assertThat(new OrderSnapshot(501L, " entregado ", 10L, 7L, 42L).isDelivered()).isTrue();
    }

    @Test
    @DisplayName("TC4: la orden puede no tener repartidor asignado")
    void courierUserId_isNullable() {
        OrderSnapshot withoutCourier = new OrderSnapshot(501L, "ENTREGADO", 10L, 7L, null);
        assertThat(withoutCourier.courierUserId()).isNull();
    }

    @Test
    @DisplayName("TC5: isParticipant del cliente y del repartidor compara por id de usuario")
    void isParticipant_comparesUserIds() {
        OrderSnapshot snapshot = delivered();
        assertThat(snapshot.isClient(10L)).isTrue();
        assertThat(snapshot.isClient(99L)).isFalse();
        assertThat(snapshot.isCourier(42L)).isTrue();
        assertThat(snapshot.isCourier(10L)).isFalse();
        assertThat(snapshot.isCourier(null)).isFalse();
    }

    @Test
    @DisplayName("TC6: el destino derivado depende del tipo de destino")
    void targetIdFor_dependsOnTargetType() {
        OrderSnapshot snapshot = delivered();
        assertThat(snapshot.targetIdFor(RatingTarget.TIENDA)).isEqualTo(7L);
        assertThat(snapshot.targetIdFor(RatingTarget.REPARTIDOR)).isEqualTo(42L);
        assertThat(snapshot.targetIdFor(RatingTarget.CLIENTE)).isEqualTo(10L);
    }

    @Test
    @DisplayName("TC7: si el repartidor no existe, el destino REPARTIDOR no se puede derivar")
    void targetIdFor_repartidorWithoutCourier_isEmpty() {
        OrderSnapshot snapshot = new OrderSnapshot(501L, "ENTREGADO", 10L, 7L, null);
        assertThat(snapshot.targetIdFor(RatingTarget.REPARTIDOR)).isNull();
    }
}
