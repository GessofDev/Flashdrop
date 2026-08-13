package com.flashdrop.observability.error;

/**
 * Catálogo de códigos de error estables, común a todos los servicios.
 * Agrupar por estos códigos (no por mensaje) es lo que hace legible el dashboard.
 */
public final class ErrorCatalog {

    private ErrorCatalog() {}

    // Transversales
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    // auth
    public static final String AUTH_INVALID_CREDENTIALS = "AUTH_INVALID_CREDENTIALS";
    public static final String AUTH_TOKEN_MISSING = "AUTH_TOKEN_MISSING";
    public static final String AUTH_TOKEN_INVALID = "AUTH_TOKEN_INVALID";
    public static final String AUTH_FORBIDDEN_ROLE = "AUTH_FORBIDDEN_ROLE";
    public static final String AUTH_EMAIL_TAKEN = "AUTH_EMAIL_TAKEN";

    // orders / delivery
    public static final String ORDER_PRODUCTS_DIFFERENT_RESTAURANT = "ORDER_PRODUCTS_DIFFERENT_RESTAURANT";
    public static final String ORDER_ALREADY_CLAIMED = "ORDER_ALREADY_CLAIMED";
    public static final String DELIVERY_NOT_A_COURIER = "DELIVERY_NOT_A_COURIER";
}
