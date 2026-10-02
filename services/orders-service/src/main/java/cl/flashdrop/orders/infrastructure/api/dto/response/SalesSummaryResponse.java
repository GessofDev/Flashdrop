package cl.flashdrop.orders.infrastructure.api.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTO de {@code GET /api/orders/restaurants/{id}/sales-summary} (spec store-flow FR-1).
 * {@code restaurantId} viaja como Long (mismo id que el path, emitido por catalog-service).
 */
@Getter
@Builder
public class SalesSummaryResponse {

    private final Long restaurantId;
    private final String range;
    private final OffsetDateTime from;
    private final OffsetDateTime to;
    private final long totalOrders;
    private final BigDecimal totalRevenue;
    private final BigDecimal averageTicket;
    private final List<TopProductDto> topProducts;

    @Getter
    @Builder
    public static class TopProductDto {
        private final UUID productId;
        private final String productName;
        private final long quantitySold;
        private final BigDecimal revenue;
    }
}
