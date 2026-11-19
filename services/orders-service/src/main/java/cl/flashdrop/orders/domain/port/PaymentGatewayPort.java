package cl.flashdrop.orders.domain.port;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Puerto de salida hacia la pasarela de pago (F8). Hoy lo implementa una pasarela simulada; cuando se
 * contrate la real, se reemplaza el adaptador sin tocar los casos de uso.
 *
 * <p>Flujo: Orders abre una sesion de pago ({@link #createSession}); el cliente paga en la pantalla de
 * la pasarela; la pasarela avisa el resultado y Orders lo verifica ({@link #verifyNotification}).</p>
 */
public interface PaymentGatewayPort {

    /** Nombre del proveedor tal como se guarda en {@code payments.provider}. */
    String providerName();

    /** Abre un cobro por {@code amount} para el pedido. El monto lo calcula el servidor. */
    PaymentSession createSession(UUID orderId, BigDecimal amount, String currency);

    /** Enlace que debe abrir la app para pagar un cobro ya abierto. */
    String paymentUrlFor(String providerTxId);

    /**
     * Valida el aviso de la pasarela (firma incluida) y lo traduce a un resultado.
     *
     * @throws cl.flashdrop.orders.domain.exception.ForbiddenOperationException si la firma no es valida
     * @throws cl.flashdrop.orders.domain.exception.OrderDomainException si el estado no se reconoce
     */
    PaymentNotification verifyNotification(String providerTxId, String status, String signature);

    record PaymentSession(String providerTxId, String paymentUrl) {
    }

    record PaymentNotification(String providerTxId, boolean approved, String rawStatus) {
    }
}
