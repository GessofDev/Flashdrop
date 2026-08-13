package cl.flashdrop.orders.infrastructure.persistence.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record OrderRow(
        Long id,
        @JsonProperty("client_id") Long clientId,
        @JsonProperty("restaurant_id") Long restaurantId,
        @JsonProperty("delivery_id") Long deliveryId,
        String status,
        String address,
        BigDecimal subtotal,
        @JsonProperty("delivery_fee") BigDecimal deliveryFee,
        BigDecimal total,
        @JsonProperty("payment_method") String paymentMethod,
        @JsonProperty("created_at") OffsetDateTime createdAt
) {}