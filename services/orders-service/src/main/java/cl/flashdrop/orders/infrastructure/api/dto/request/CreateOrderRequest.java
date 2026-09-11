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

/**
 * DTO de entrada para la creación de un pedido.
 * Compatible con el formato actual enviado por la app Flutter.
 * Soporta tanto el formato legacy (product_id + quantity) como el nuevo (items[]).
 *
 * <p><b>Wire shape (GAP-04 + alineación con el resto del sistema):</b>
 * {@code userId} y {@code productId} son <b>Long</b> en el wire, no UUID. Esto
 * coincide con lo que emiten catalog-service ({@code productId: 8}) y auth-service
 * ({@code userId: 8}, el "8" del usuario de prueba que registró Nicolás). UUID sólo
 * vive en el dominio interno de Orders; la conversión al límite la hace
 * {@link cl.flashdrop.orders.infrastructure.adapter.outbound.IdConverter} en
 * {@code OrderController}. Es el mismo criterio que ya cerró D5/D6 para el
 * endpoint interno {@code /api/internal/orders/claim}.</p>
 */
@Getter
@Setter
@NoArgsConstructor
public class CreateOrderRequest {

    /**
     * ID del usuario que envía el pedido (wire: Long, mismo número que emite auth en
     * el subject del JWT).
     *
     * @deprecated GAP-04 (auditoría 2026-09-04): este campo ya NO se usa para crear el
     * pedido — {@code OrderController.createOrder()} siempre usa el userId resuelto desde
     * el JWT autenticado ({@code CurrentUserResolver}), nunca este valor. Se conserva sólo
     * por compatibilidad de forma del JSON con clientes existentes que lo envían.
     */
    @Deprecated
    @JsonAlias("user_id")
    private Long userId;

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
    private Long productId;

    /** Cantidad en formato legacy */
    private Integer quantity;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class ItemRequest {

        @JsonAlias("product_id")
        @jakarta.validation.constraints.NotNull(message = "El ID de producto es obligatorio")
        private Long productId;

        @Positive(message = "La cantidad debe ser mayor a 0")
        private int quantity = 1;
    }
}
