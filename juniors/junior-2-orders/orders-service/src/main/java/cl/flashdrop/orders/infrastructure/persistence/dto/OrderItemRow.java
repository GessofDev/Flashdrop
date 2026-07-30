package cl.flashdrop.orders.infrastructure.persistence.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record OrderItemRow(
        Long id,
        @JsonProperty("order_id") Long orderId,
        @JsonProperty("product_id") Long productId,
        Integer quantity,
        @JsonProperty("unit_price") BigDecimal unitPrice,
        BigDecimal total
) {}