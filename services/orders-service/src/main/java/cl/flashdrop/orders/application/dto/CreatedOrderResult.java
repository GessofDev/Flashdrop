package cl.flashdrop.orders.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Resultado de la creación de un pedido.
 *
 * <p>{@code deliveryCode} solo viene en modo SEGURO: es el código que el cliente debe entregar al
 * repartidor. En modo NORMAL es {@code null} (el código fijo lo completa la app del repartidor).</p>
 *
 * <p>El pedido nace "Pendiente de pago": {@code paymentUrl} es el enlace de la pasarela que la app
 * debe abrir, y {@code paymentExpiresAt} el limite para pagar. {@code deliveryFee} es el envio ya
 * calculado por el servidor.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreatedOrderResult(UUID id, BigDecimal total, String receptionMode, String deliveryCode,
                                 String status, BigDecimal deliveryFee, Long paymentId, String paymentUrl,
                                 OffsetDateTime paymentExpiresAt) {

    public CreatedOrderResult(UUID id, BigDecimal total, String receptionMode, String deliveryCode) {
        this(id, total, receptionMode, deliveryCode, null, null, null, null, null);
    }

    public CreatedOrderResult(UUID id, BigDecimal total) {
        this(id, total, "NORMAL", null);
    }
}
