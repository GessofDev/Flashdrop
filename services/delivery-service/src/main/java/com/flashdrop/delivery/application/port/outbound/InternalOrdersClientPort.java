package com.flashdrop.delivery.application.port.outbound;

import com.flashdrop.delivery.domain.exception.OrderClaimFailedException;

import java.util.List;

/**
 * Outbound port for claiming orders against orders-service.
 *
 * <p>Used only when the feature flag
 * {@code delivery.claim.delegate-to-orders.enabled=true}. The implementation
 * hits {@code POST /api/internal/orders/claim} behind {@code X-Internal-Api-Key}
 * with the wire shape
 * {@code {"userId": <long>, "orderIds": [<long>, ...]}} (plan D5, D7).
 *
 * <p>The {@code userId} is the courier's {@code user_id} (Long from JWT subject),
 * NOT {@code delivery.id}. Orders resolves {@code userId} → {@code delivery.id}
 * via its own canonical {@code DeliveryPort.findDeliveryIdByUserId(Long)} lookup
 * against delivery's {@code GET /api/internal/delivery-persons?userId=...}
 * (migration plan §3.4).
 */
public interface InternalOrdersClientPort {

    /**
     * Notify orders-service that the courier identified by {@code userId} has
     * claimed the given {@code orderIds}. Throws {@link OrderClaimFailedException}
     * on every non-2xx response or network failure.
     */
    void claimOrders(Long userId, List<Long> orderIds) throws OrderClaimFailedException;
}
