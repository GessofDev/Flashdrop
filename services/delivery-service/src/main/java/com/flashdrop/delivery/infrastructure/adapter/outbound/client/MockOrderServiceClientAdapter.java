package com.flashdrop.delivery.infrastructure.adapter.outbound.client;

import com.flashdrop.delivery.application.port.outbound.OrderServicePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Mock implementation of {@link OrderServicePort} for local development and testing.
 *
 * <p>Activated when:
 * <ul>
 *   <li>{@code orders.service.mock=true} property is set, OR</li>
 *   <li>Spring profile {@code mock-orders} is active</li>
 * </ul>
 *
 * <p>Always returns an empty list — suitable for:
 * <ul>
 *   <li>Automated tests where orders-service is unavailable</li>
 *   <li>Local Postman testing without running orders-service</li>
 *   <li>CI pipelines where only delivery-service is being tested</li>
 * </ul>
 */
@Component
@Profile("mock-orders")
public class MockOrderServiceClientAdapter implements OrderServicePort {

    private static final Logger log = LoggerFactory.getLogger(MockOrderServiceClientAdapter.class);

    public MockOrderServiceClientAdapter() {
        log.info("MockOrderServiceClientAdapter activated — all order lookups return empty list");
    }

    @Override
    public List<OrderInfo> getOrdersByIds(List<Long> orderIds) {
        log.debug("Mock: getOrdersByIds({}) — returning empty list", orderIds);
        return List.of();
    }

    @Override
    public boolean areOrdersFromSameRestaurant(List<Long> orderIds) {
        log.debug("Mock: areOrdersFromSameRestaurant({}) — returning true", orderIds);
        return true;
    }
}
