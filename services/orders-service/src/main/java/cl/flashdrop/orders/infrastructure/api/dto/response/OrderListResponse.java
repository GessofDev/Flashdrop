package cl.flashdrop.orders.infrastructure.api.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTO para la respuesta abreviada de pedidos en listados.
 */
@Getter
@Builder
public class OrderListResponse {

    private final UUID id;
    private final String code;
    private final String status;
    private final String address;
    private final String commune;

    /** Si el sistema cancelo solo el pedido (F7): codigo del motivo, texto para mostrar y cuando. Sino, ausentes. */
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private final String cancelReason;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private final String cancelReasonMessage;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private final OffsetDateTime cancelledAt;

    private final BigDecimal subtotal;
    private final BigDecimal deliveryFee;
    private final BigDecimal total;
    private final String paymentMethod;
    private final String clientName;
    private final String clientPhone;
    private final String clientEmail;
    private final String restaurantName;
    private final UUID deliveryId;
    private final String receptionMode;
    private final OffsetDateTime createdAt;
}
