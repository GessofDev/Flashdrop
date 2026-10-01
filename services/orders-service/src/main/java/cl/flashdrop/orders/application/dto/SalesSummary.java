package cl.flashdrop.orders.application.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Resumen de ventas de un restaurante (PR-orders-metrics, spec store-flow FR-1).
 * Solo considera pedidos ENTREGADO. {@code totalRevenue} es la suma de los subtotales de
 * productos (sin costo de despacho).
 */
public record SalesSummary(
        UUID restaurantId,
        String range,
        OffsetDateTime from,
        OffsetDateTime to,
        long totalOrders,
        BigDecimal totalRevenue,
        BigDecimal averageTicket,
        List<TopProduct> topProducts
) {

    /** Producto más vendido en el período, por cantidad. */
    public record TopProduct(UUID productId, String productName, long quantitySold, BigDecimal revenue) {}
}
