package cl.flashdrop.orders.infrastructure.api;

import cl.flashdrop.orders.domain.exception.OrderDomainException;
import cl.flashdrop.orders.infrastructure.adapter.outbound.IdConverter;

import java.util.UUID;

/**
 * Resuelve el identificador de un pedido desde el path variable de la API.
 *
 * <p>El servicio expone el id del pedido en dos formatos y ambos son válidos en la
 * URL:</p>
 *
 * <ul>
 *   <li><b>Numérico</b> (el público): es el que emite Delivery en
 *       {@code RouteResponse.orderId}, que es {@code Long}. Es el id que la app del
 *       repartidor tiene en pantalla.</li>
 *   <li><b>UUID</b> (el interno del dominio): es el que usan los casos de uso y las
 *       entidades de Orders.</li>
 * </ul>
 *
 * <p>El criterio es el mismo que ya aplican {@code GET /api/orders/{id}},
 * {@code PUT /api/orders/{id}/status} y {@code POST /api/orders/{id}/deliver} desde el
 * issue #62: primero se interpreta como número y se convierte con
 * {@link IdConverter#toUuid(long)}; si no es numérico se interpreta como UUID; si no es
 * ninguno de los dos, es un error de dominio y responde 400.</p>
 *
 * <p>La conversión es el mismo mapeo en las dos direcciones: {@code toUuid(501)} produce
 * el UUID cuyo id numérico es 501, que es el que el dominio de Orders ya usa.</p>
 */
public final class OrderIdResolver {

    private OrderIdResolver() {
    }

    /**
     * Convierte el id crudo del path variable al UUID interno del dominio.
     *
     * @param rawOrderId id numérico o UUID, tal como llega en la URL
     * @return el UUID interno del pedido
     * @throws OrderDomainException si el valor no es un número ni un UUID válido
     */
    public static UUID resolve(String rawOrderId) {
        if (rawOrderId == null || rawOrderId.isBlank()) {
            throw new OrderDomainException("Id de pedido invalido");
        }
        try {
            return IdConverter.toUuid(Long.parseLong(rawOrderId));
        } catch (NumberFormatException ignored) {
            try {
                return UUID.fromString(rawOrderId);
            } catch (IllegalArgumentException ex) {
                throw new OrderDomainException("Id de pedido invalido");
            }
        }
    }
}
