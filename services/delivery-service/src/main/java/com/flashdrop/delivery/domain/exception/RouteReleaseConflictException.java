package com.flashdrop.delivery.domain.exception;

/**
 * A route release or cancellation conflicts with the route's current state.
 *
 * <p>The internal caller retries only when the conflict is caused by a
 * transient upstream failure. A different courier or a terminal route is a
 * stable conflict and must be surfaced as HTTP 409.</p>
 */
public class RouteReleaseConflictException extends RuntimeException {

    public RouteReleaseConflictException(Long orderId) {
        super("Route release conflicts with the current assignment for order ID: " + orderId);
    }

    public RouteReleaseConflictException(Long routeId, String message) {
        super("Route " + routeId + " cannot change state: " + message);
    }
}
