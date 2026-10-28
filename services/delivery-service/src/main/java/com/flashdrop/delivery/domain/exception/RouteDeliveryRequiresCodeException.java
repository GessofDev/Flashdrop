package com.flashdrop.delivery.domain.exception;

/**
 * A courier tried to mark a route as delivered through the public API. A delivery is only
 * confirmed with the customer's verification code, via orders-service
 * ({@code POST /api/orders/{id}/deliver}); orders-service then syncs this route through the
 * internal API, which is the only path to ENTREGADO.
 */
public class RouteDeliveryRequiresCodeException extends RuntimeException {

    public RouteDeliveryRequiresCodeException() {
        super("A delivery is confirmed with the customer's verification code "
                + "(POST /api/orders/{id}/deliver), not by updating the route status");
    }
}
