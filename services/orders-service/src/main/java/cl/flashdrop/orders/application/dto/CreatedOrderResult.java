package cl.flashdrop.orders.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Resultado de la creación de un pedido.
 *
 * <p>{@code deliveryCode} solo viene en modo SEGURO: es el código que el cliente debe entregar al
 * repartidor. En modo NORMAL es {@code null} (el código fijo lo completa la app del repartidor).</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreatedOrderResult(UUID id, BigDecimal total, String receptionMode, String deliveryCode) {

    public CreatedOrderResult(UUID id, BigDecimal total) {
        this(id, total, "NORMAL", null);
    }
}
