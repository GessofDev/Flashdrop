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
    private final BigDecimal subtotal;
    private final BigDecimal deliveryFee;
    private final BigDecimal total;
    private final String paymentMethod;
    private final String clientName;
    private final String clientPhone;
    private final String clientEmail;
    private final String restaurantName;
    private final UUID deliveryId;
    private final OffsetDateTime createdAt;
}
