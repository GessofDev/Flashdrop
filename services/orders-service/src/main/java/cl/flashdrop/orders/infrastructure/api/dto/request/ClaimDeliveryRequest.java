package cl.flashdrop.orders.infrastructure.api.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

/**
 * DTO de entrada para que un repartidor tome un pedido para delivery.
 *
 * Formato principal (openapi.yaml):
 * {@code { "deliveryPersonId": "...", "orderId": "..." }}
 *
 * Formato legacy compatible (Node.js original):
 * {@code { "user_id": "...", "order_ids": ["..."] }}
 */
@Getter
@Setter
@NoArgsConstructor
public class ClaimDeliveryRequest {

    /**
     * ID del repartidor (formato openapi.yaml).
     * Alias {@code user_id} para compatibilidad con el formato legacy.
     */
    @NotNull(message = "El deliveryPersonId es obligatorio")
    @JsonAlias({"user_id", "userId"})
    private UUID deliveryPersonId;

    /**
     * ID del pedido a reclamar (formato openapi.yaml, pedido único).
     * Si se usa formato legacy con {@code order_ids}, se toma el primero.
     */
    @JsonAlias({"order_id"})
    private UUID orderId;

    /**
     * Lista de IDs de pedidos (formato legacy).
     * Si se provee, se fusiona con {@code orderId}.
     */
    @JsonAlias({"order_ids"})
    private List<UUID> orderIds;

    /**
     * Retorna la lista efectiva de orderIds para el caso de uso.
     * Soporta tanto el formato openapi.yaml (orderId individual)
     * como el formato legacy (orderIds lista).
     */
    public List<UUID> resolvedOrderIds() {
        if (orderIds != null && !orderIds.isEmpty()) {
            return orderIds;
        }
        if (orderId != null) {
            return List.of(orderId);
        }
        return List.of();
    }

    /**
     * @deprecated GAP-03 (auditoría 2026-09-04): {@code DeliveryController} ya NO usa este
     * valor para resolver la identidad del repartidor — se resuelve exclusivamente desde el
     * JWT autenticado ({@code CurrentUserResolver}), para cerrar el IDOR que permitía
     * reclamar pedidos indicando cualquier {@code deliveryPersonId}/{@code user_id} ajeno en
     * el body. El campo se conserva sólo por compatibilidad de forma del JSON con clientes
     * existentes (no se elimina la validación {@code @NotNull} para no romper requests que
     * ya lo envían), pero su contenido es ignorado para autorización.
     */
    @Deprecated
    public UUID getUserId() {
        return deliveryPersonId;
    }
}

