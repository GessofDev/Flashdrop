package cl.flashdrop.orders.infrastructure.adapter.outbound.http.dto;

import java.math.BigDecimal;

/**
 * DTO de transporte interno de Catalog para restaurants.
 */
public record InternalRestaurantDto(
        Long id,
        String name,
        String address,
        Long userId,
        Boolean open,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal deliveryRadiusKm,
        Long merchantId
) {
    public InternalRestaurantDto(Long id, String name, String address, Long userId, Boolean open) {
        this(id, name, address, userId, open, null, null, null, null);
    }

    public InternalRestaurantDto(Long id, String name, String address, Long userId, Boolean open,
                                 BigDecimal latitude, BigDecimal longitude) {
        this(id, name, address, userId, open, latitude, longitude, null, null);
    }

    public InternalRestaurantDto(Long id, String name, String address, Long userId, Boolean open,
                                 BigDecimal latitude, BigDecimal longitude, BigDecimal deliveryRadiusKm) {
        this(id, name, address, userId, open, latitude, longitude, deliveryRadiusKm, null);
    }
}
