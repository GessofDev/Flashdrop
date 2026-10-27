package cl.flashdrop.orders.domain.port;

import cl.flashdrop.orders.domain.model.Order;

/**
 * Puerto que entrega el código de verificación vigente de un pedido.
 *
 * <p>El código no se guarda: se deriva del pedido y de la versión vigente
 * ({@link Order#getDeliveryCodeVersion()}). Para los pedidos en modo NORMAL es siempre
 * {@link Order#DEFAULT_DELIVERY_CODE}.</p>
 */
public interface DeliveryCodePort {

    /** Código vigente del pedido (6 dígitos). */
    String currentCodeFor(Order order);
}
