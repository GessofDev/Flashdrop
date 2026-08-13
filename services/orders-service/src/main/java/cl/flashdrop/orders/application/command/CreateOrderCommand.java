package cl.flashdrop.orders.application.command;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Comando de entrada para el caso de uso de creación de pedido.
 * Transporta los datos necesarios desde el adaptador REST hacia el caso de uso.
 */
@Getter
@Builder
public class CreateOrderCommand {

    /** ID del usuario que realiza el pedido (puede ser null en modo demo) */
    private final UUID userId;

    /** Dirección de entrega */
    private final String address;

    /** Método de pago (string: "Efectivo", "Tarjeta", etc.) */
    private final String paymentMethod;

    /** Distancia estimada en km (opcional, se usa el valor por defecto si es null) */
    private final BigDecimal distanceKm;

    /** Tiempo estimado en minutos (opcional, se usa el valor por defecto si es null) */
    private final Integer estimatedMinutes;

    /** Lista de ítems del pedido */
    private final List<ItemRequest> items;

    @Getter
    @Builder
    public static class ItemRequest {
        private final UUID productId;
        private final int quantity;
    }
}
