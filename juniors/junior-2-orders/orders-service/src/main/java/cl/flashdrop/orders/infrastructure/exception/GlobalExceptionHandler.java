package cl.flashdrop.orders.infrastructure.exception;

import cl.flashdrop.orders.domain.exception.OrderDomainException;
import cl.flashdrop.orders.infrastructure.api.dto.response.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Manejador global de excepciones para orders-service.
 *
 * <p>Retorna la estructura {@code { "status": int, "error": "CONSTANTE", "message": "..." } }
 * definida en {@code MIGRATION_PLAN.md} §10 ("Códigos de error estándar"). El código {@code error}
 * usa las constantes de esa tabla (BAD_REQUEST, UNAUTHORIZED, FORBIDDEN, NOT_FOUND, CONFLICT,
 * VALIDATION_ERROR, INTERNAL_ERROR, SERVICE_UNAVAILABLE), no la frase de razón HTTP.</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OrderDomainException.class)
    public ResponseEntity<ErrorResponse> handleDomainException(OrderDomainException ex) {
        log.warn("Excepción de dominio de pedido capturada: {}", ex.getMessage());
        String msg = ex.getMessage();
        // Mapeo dinámico de estados HTTP para coincidir con las reglas de negocio existentes.
        // Comparación case-insensitive: los mensajes de dominio no siguen una convención de
        // capitalización fija (ej. "Ya tienes..." vs. "ya fueron tomados...").
        String lowerMsg = msg.toLowerCase(Locale.ROOT);
        HttpStatus status = HttpStatus.BAD_REQUEST;
        if (lowerMsg.contains("no encontrado") || lowerMsg.contains("no existen")
                || lowerMsg.contains("no disponibles") || lowerMsg.contains("no estan disponibles")) {
            status = HttpStatus.NOT_FOUND;
        } else if (lowerMsg.contains("perfil de repartidor")) {
            status = HttpStatus.FORBIDDEN;
        } else if (lowerMsg.contains("ya tienes") || lowerMsg.contains("ya fueron tomados") || lowerMsg.contains("alguien tomo")) {
            status = HttpStatus.CONFLICT;
        }

        return build(status, msg);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("Error de validación de argumentos REST: {}", details);
        return build(HttpStatus.BAD_REQUEST, details);
    }

    @ExceptionHandler(ExternalServiceException.class)
    public ResponseEntity<ErrorResponse> handleExternalService(ExternalServiceException ex) {
        log.warn("Fallo en servicio interno externo: {}", ex.getMessage());
        return build(ex.getStatus(), ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Error no controlado capturado: ", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor: " + ex.getMessage());
    }

    private static ResponseEntity<ErrorResponse> build(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(
                ErrorResponse.builder()
                        .status(status.value())
                        .error(errorCodeFor(status))
                        .message(message)
                        .build()
        );
    }

    /**
     * Traduce un {@link HttpStatus} a la constante de {@code error} de MIGRATION_PLAN.md §10.
     * Códigos fuera de la tabla (ej. 502 de un upstream caído) caen al bucket más cercano.
     */
    private static String errorCodeFor(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "BAD_REQUEST";
            case UNAUTHORIZED -> "UNAUTHORIZED";
            case FORBIDDEN -> "FORBIDDEN";
            case NOT_FOUND -> "NOT_FOUND";
            case CONFLICT -> "CONFLICT";
            case UNPROCESSABLE_ENTITY -> "VALIDATION_ERROR";
            case SERVICE_UNAVAILABLE, BAD_GATEWAY, GATEWAY_TIMEOUT -> "SERVICE_UNAVAILABLE";
            default -> status.is5xxServerError() ? "INTERNAL_ERROR" : "BAD_REQUEST";
        };
    }
}
