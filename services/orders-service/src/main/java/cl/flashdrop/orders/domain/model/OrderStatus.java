package cl.flashdrop.orders.domain.model;

import lombok.Getter;

/**
 * Estados válidos de un pedido en el sistema FlashDrop.
 * El valor en español corresponde al string almacenado en la base de datos
 * y retornado al cliente Flutter para mantener compatibilidad.
 */
@Getter
public enum OrderStatus {

    NUEVO_PEDIDO("Nuevo pedido"),
    PREPARANDO("Preparando"),
    LISTO_PARA_RETIRO("Listo para retiro"),
    RETIRADO("Retirado"),
    EN_CAMINO("En camino"),
    ENTREGADO("Entregado");

    private final String value;

    OrderStatus(String value) {
        this.value = value;
    }

    /**
     * Obtiene el enum a partir del string almacenado en base de datos.
     *
     * @param value el valor en español del estado
     * @return el enum correspondiente
     * @throws IllegalArgumentException si el estado no es reconocido
     */
    public static OrderStatus fromValue(String value) {
        for (OrderStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Estado de pedido no reconocido: " + value);
    }

    /**
     * Indica si el pedido ya fue asignado a un repartidor y está en tránsito o entregado.
     */
    public boolean isActiveDelivery() {
        return this == EN_CAMINO || this == RETIRADO;
    }

    /**
     * Indica si el pedido ya fue finalizado y no puede volver a ser tomado.
     */
    public boolean isClosed() {
        return this == ENTREGADO || this == EN_CAMINO || this == RETIRADO;
    }
}
