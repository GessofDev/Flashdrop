package com.flashdrop.delivery.application.port.outbound;

import java.util.List;

public interface OrderServicePort {

    List<OrderInfo> getOrdersByIds(List<Long> orderIds);

    boolean areOrdersFromSameRestaurant(List<Long> orderIds);

    record OrderInfo(Long id, Long restaurantId, String pickupAddress, String deliveryAddress, String code) {
    }
}
