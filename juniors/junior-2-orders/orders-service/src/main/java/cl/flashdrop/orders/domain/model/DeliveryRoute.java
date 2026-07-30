package cl.flashdrop.orders.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Ruta de entrega asociada a un pedido.
 * Contiene información logística del trayecto desde el restaurante hasta el cliente.
 *
 * Diseñada para ser gestionada por Orders en esta fase inicial.
 * En el futuro puede migrarse a un Delivery Service dedicado
 * reemplazando únicamente el adaptador de persistencia.
 */
@Getter
@Builder
public class DeliveryRoute {

    private final UUID id;

    /** ID del pedido al que pertenece esta ruta */
    private final UUID orderId;

    /** Dirección de recolección (restaurante) */
    private final String pickupAddress;

    /** Dirección de entrega (cliente) */
    private final String deliveryAddress;

    /** Distancia estimada en kilómetros */
    private final BigDecimal distanceKm;

    /** Tiempo estimado de entrega en minutos */
    private final int estimatedMinutes;

    /** Estado de la ruta (espejo del estado del pedido) */
    private final String status;
}
