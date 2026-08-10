package cl.flashdrop.orders.infrastructure.api.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTO detallado para la respuesta de un pedido individual.
 */
@Getter
@Builder
public class OrderDetailResponse {

    private final UUID id;
    private final String code;
    private final String status;
    private final String address;
    private final BigDecimal subtotal;
    private final BigDecimal deliveryFee;
    private final BigDecimal total;
    private final String paymentMethod;
    private final OffsetDateTime createdAt;

    private final ClientDto client;
    private final RestaurantDto restaurant;
    private final DeliveryDto delivery;
    private final RouteDto route;
    private final List<ItemDto> items;

    @Getter
    @Builder
    public static class ClientDto {
        private final String name;
        private final String email;
        private final String phone;
    }

    @Getter
    @Builder
    public static class RestaurantDto {
        private final String name;
        private final String address;
    }

    @Getter
    @Builder
    public static class DeliveryDto {
        private final String name;
        private final String phone;
        private final String vehicle;
    }

    @Getter
    @Builder
    public static class RouteDto {
        private final String pickupAddress;
        private final String deliveryAddress;
        private final BigDecimal distanceKm;
        private final Integer estimatedMinutes;
        private final String status;
    }

    @Getter
    @Builder
    public static class ItemDto {
        private final UUID id;
        private final String name;
        private final String description;
        private final String image;
        private final int quantity;
        private final BigDecimal unitPrice;
        private final BigDecimal total;
    }
}
