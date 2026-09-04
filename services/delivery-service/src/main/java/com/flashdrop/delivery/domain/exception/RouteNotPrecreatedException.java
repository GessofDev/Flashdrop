package com.flashdrop.delivery.domain.exception;

/**
 * Plan §9.5 D8: the claim looks up the route pre-created by Orders (via
 * C-6: {@code POST /api/internal/routes}). When no row exists for the given
 * {@code orderId} it means Orders has not yet published the order to
 * Delivery — the courier's claim is rejected with a structured 409 so the
 * caller can retry after Orders eventually publishes the route.
 *
 * <p>This is intentionally a separate exception type from
 * {@link RouteAlreadyAssignedException}: the cause differs ("Orders hasn't
 * created it yet" vs. "another courier already grabbed it") and we want
 * the API to surface that distinction to the courier / dashboard.
 */
public class RouteNotPrecreatedException extends RuntimeException {

    public RouteNotPrecreatedException(Long orderId) {
        super("Route has not been pre-created for order ID: " + orderId);
    }
}
