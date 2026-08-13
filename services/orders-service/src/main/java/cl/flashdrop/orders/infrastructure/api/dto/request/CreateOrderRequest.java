package cl.flashdrop.orders.infrastructure.api.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * DTO de entrada para la creación de un pedido.
 * Compatible con el formato actual enviado por la app Flutter.
 * Soporta tanto el formato legacy (product_id + quantity) como el nuevo (items[]).
 */
@Getter
@Setter
@NoArgsConstructor
public class CreateOrderRequest {

    /** ID del usuario (opcional, se resuelve por fallback en el servidor) */
    @JsonAlias("user_id")
    private UUID userId;

    /** Dirección de entrega (obligatorio) */
    @NotBlank(message = "La dirección es obligatoria")
    private String address;

    /** Método de pago: Efectivo, Tarjeta, Transferencia */
    @JsonAlias("payment_method")
    private String paymentMethod;

    /** Distancia estimada en km (opcional) */
    @JsonAlias("distance_km")
    private BigDecimal distanceKm;

    /** Tiempo estimado en minutos (opcional) */
    @JsonAlias("estimated_minutes")
    private Integer estimatedMinutes;

    /** Lista de ítems (formato nuevo) */
    private List<ItemRequest> items;

    /** ID del producto en formato legacy (1 solo producto) */
    @JsonAlias("product_id")
    private UUID productId;

    /** Cantidad en formato legacy */
    private Integer quantity;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class ItemRequest {

        @JsonAlias("product_id")
        @jakarta.validation.constraints.NotNull(message = "El ID de producto es obligatorio")
        private UUID productId;

        @Positive(message = "La cantidad debe ser mayor a 0")
        private int quantity = 1;
    }
}
