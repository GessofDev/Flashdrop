package cl.flashdrop.orders.domain.model;

import cl.flashdrop.orders.domain.exception.OrderDomainException;
import lombok.Getter;

/**
 * Métodos de pago del modelo de FlashDrop.
 * El valor en español es el string almacenado en la base de datos.
 *
 * <p>Hoy el sistema solo <b>acepta cobros con Tarjeta</b> ({@link #fromRequest}). Efectivo y
 * Transferencia siguen en el modelo porque pueden aceptarse en el futuro y porque hay pedidos
 * historicos con esos valores ({@link #fromValue}), pero ningun pedido nuevo puede traerlos.</p>
 */
@Getter
public enum PaymentMethod {

    EFECTIVO("Efectivo"),
    TARJETA("Tarjeta"),
    TRANSFERENCIA("Transferencia");

    private final String value;

    PaymentMethod(String value) {
        this.value = value;
    }

    /** Si el sistema acepta cobrar con este metodo hoy. */
    public boolean isAcceptedForCharge() {
        return this == TARJETA;
    }

    /**
     * Lee un valor guardado en base de datos (pedidos historicos incluidos). No valida si se acepta:
     * un valor vacio o desconocido se lee como Efectivo, como siempre.
     */
    public static PaymentMethod fromValue(String value) {
        if (value == null || value.isBlank()) {
            return EFECTIVO;
        }
        for (PaymentMethod method : values()) {
            if (method.value.equalsIgnoreCase(value.trim())) {
                return method;
            }
        }
        return EFECTIVO;
    }

    /**
     * Interpreta el metodo que envia la app al crear un pedido. Sin valor se asume Tarjeta; cualquier
     * otro metodo (Efectivo, Transferencia o desconocido) se rechaza con un error claro.
     */
    public static PaymentMethod fromRequest(String value) {
        if (value == null || value.isBlank()) {
            return TARJETA;
        }
        for (PaymentMethod method : values()) {
            if (method.value.equalsIgnoreCase(value.trim()) && method.isAcceptedForCharge()) {
                return method;
            }
        }
        throw new OrderDomainException("Medio de pago no disponible: por ahora solo se acepta Tarjeta");
    }
}
