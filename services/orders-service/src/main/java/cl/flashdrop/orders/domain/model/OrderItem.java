package cl.flashdrop.orders.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Ítem de un pedido en el dominio de Orders.
 * Contiene la referencia al producto y el cálculo de precio por línea.
 * Es inmutable una vez creado: el precio unitario se fija en el momento de la orden.
 */
@Getter
@Builder
public class OrderItem {

    private final UUID id;

    /** ID del producto en el catálogo */
    private final UUID productId;

    /** Nombre del producto (snapshot al momento del pedido) */
    private final String productName;

    /** Descripción del producto (snapshot al momento del pedido) */
    private final String productDescription;

    /** URL de imagen del producto */
    private final String productImage;

    /** Cantidad solicitada */
    private final int quantity;

    /** Precio unitario tomado del catálogo en el momento de la creación del pedido */
    private final BigDecimal unitPrice;

    /** Total de la línea = unitPrice * quantity */
    private final BigDecimal lineTotal;

    /**
     * Calcula el total de la línea a partir del precio unitario y la cantidad.
     * Este método se usa en construcción cuando lineTotal no viene precalculado.
     */
    public static BigDecimal calculateLineTotal(BigDecimal unitPrice, int quantity) {
        if (unitPrice == null || quantity <= 0) {
            return BigDecimal.ZERO;
        }
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
