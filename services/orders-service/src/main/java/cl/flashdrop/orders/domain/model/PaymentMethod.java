package cl.flashdrop.orders.domain.model;

import lombok.Getter;

/**
 * Métodos de pago aceptados por FlashDrop.
 * El valor en español es el string almacenado en la base de datos.
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
}
