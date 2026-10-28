package cl.flashdrop.orders.application.dto;

/**
 * Resultado de intentar confirmar una entrega con código.
 *
 * <p>Un código incorrecto NO se modela como excepción: el intento fallido debe quedar
 * persistido, y una excepción haría rollback del contador.</p>
 *
 * @param outcome           qué pasó
 * @param remainingAttempts intentos que quedan sobre el código vigente
 */
public record DeliveryCodeVerification(Outcome outcome, int remainingAttempts) {

    public enum Outcome {
        /** Código correcto: el pedido quedó ENTREGADO. */
        DELIVERED,
        /** Código incorrecto: se descontó un intento. */
        WRONG_CODE,
        /** Código incorrecto y se agotaron los intentos: el código se anuló y se emitió uno nuevo. */
        CODE_RENEWED,
        /** Se agotaron los intentos del último código: el pedido queda bloqueado para soporte. */
        LOCKED
    }
}
