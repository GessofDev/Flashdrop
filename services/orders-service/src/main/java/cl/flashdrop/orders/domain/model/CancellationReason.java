package cl.flashdrop.orders.domain.model;

import lombok.Getter;

/**
 * Motivo por el que el sistema cancela solo un pedido que nadie atendio (F7). El codigo se guarda en
 * {@code orders.cancel_reason}; el mensaje es lo que ve el cliente junto al estado "Cancelado".
 */
@Getter
public enum CancellationReason {

    NO_COURIER("No se encontro un repartidor a tiempo");

    private final String message;

    CancellationReason(String message) {
        this.message = message;
    }

    /** Lee el codigo guardado; {@code null} si no hay motivo o es desconocido. */
    public static CancellationReason fromCode(String code) {
        if (code == null) return null;
        for (CancellationReason reason : values()) {
            if (reason.name().equals(code)) return reason;
        }
        return null;
    }
}
