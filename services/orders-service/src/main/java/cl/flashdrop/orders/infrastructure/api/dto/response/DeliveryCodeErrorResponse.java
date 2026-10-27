package cl.flashdrop.orders.infrastructure.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

/**
 * Error al confirmar una entrega con código. Mantiene la forma estándar
 * {@code {status, error, message}} y agrega lo que la app del repartidor necesita mostrar.
 *
 * @param remainingAttempts intentos que quedan sobre el código vigente
 * @param codeRenewed       {@code true} si se agotaron los intentos y se emitió un código nuevo al cliente
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeliveryCodeErrorResponse(
        int status,
        String error,
        String message,
        Integer remainingAttempts,
        Boolean codeRenewed) {
}
