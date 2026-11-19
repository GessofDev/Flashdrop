package cl.flashdrop.orders.infrastructure.api.dto.response;

import java.math.BigDecimal;

/**
 * Public contract for an order a courier can claim.
 *
 * <p>{@code orderId} and {@code restaurantId} stay numeric because they are
 * consumed by catalog-service and delivery-service. Orders keeps UUIDs inside
 * the domain and converts them only at this API boundary.</p>
 */
public record AvailableDeliveryOrderResponse(
        Long orderId,
        Long restaurantId,
        String code,
        String status,
        String restaurantName,
        String pickupAddress,
        String deliveryAddress,
        String clientName,
        BigDecimal total,
        Double distanceKm,
        Integer estimatedMinutes,
        String receptionMode,
        BigDecimal pickupLatitude,
        BigDecimal pickupLongitude
) {
    public AvailableDeliveryOrderResponse(
            Long orderId,
            Long restaurantId,
            String code,
            String status,
            String restaurantName,
            String pickupAddress,
            String deliveryAddress,
            String clientName,
            BigDecimal total,
            Double distanceKm,
            Integer estimatedMinutes,
            String receptionMode
    ) {
        this(orderId, restaurantId, code, status, restaurantName, pickupAddress,
                deliveryAddress, clientName, total, distanceKm, estimatedMinutes, receptionMode,
                null, null);
    }
}
