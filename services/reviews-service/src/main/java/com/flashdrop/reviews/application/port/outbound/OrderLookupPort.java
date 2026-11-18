package com.flashdrop.reviews.application.port.outbound;

import com.flashdrop.reviews.domain.model.OrderSnapshot;

/**
 * Puerto de salida para consultar una orden en orders-service.
 *
 * <p>Implementado por el cliente HTTP contra
 * {@code GET /api/internal/orders/{id}} (T4), protegido con
 * {@code X-Internal-Api-Key}.</p>
 *
 * <p>Degradación segura: si orders-service no responde, la implementación
 * lanza {@link com.flashdrop.reviews.domain.exception.InternalServiceUnavailableException}
 * (HTTP 503). reviews-service <b>nunca</b> valida contra datos inventados.</p>
 */
public interface OrderLookupPort {

    /**
     * @param orderId id numérico público de la orden.
     * @return la vista de la orden con su estado y sus participantes.
     * @throws com.flashdrop.reviews.domain.exception.OrderNotFoundException si la orden no existe.
     * @throws com.flashdrop.reviews.domain.exception.InternalServiceUnavailableException
     *         si orders-service no está disponible.
     */
    OrderSnapshot findOrder(Long orderId);
}
