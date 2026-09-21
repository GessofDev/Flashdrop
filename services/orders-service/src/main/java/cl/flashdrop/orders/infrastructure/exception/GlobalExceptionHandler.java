package cl.flashdrop.orders.infrastructure.exception;

import cl.flashdrop.orders.domain.exception.OrderDomainException;
import cl.flashdrop.orders.infrastructure.api.dto.response.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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

    /**
     * GAP-03/GAP-04 (auditoría 2026-09-04): {@link cl.flashdrop.orders.infrastructure.api.CurrentUserResolver}
     * y los controllers lanzan esta excepción de Spring Security cuando el usuario autenticado intenta operar
     * con una identidad que no es la suya (ej. userId ajeno en {@code POST /api/orders} o
     * {@code GET /api/orders?user_id=}). Se mapea a 403/FORBIDDEN, no al 500 genérico que
     * produciría el handler de {@code Exception.class} si esta excepción no se declarara aquí.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Acceso denegado: {}", ex.getMessage());
        return build(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    /**
     * Payload malformado (JSON inválido, tipo de campo que no matchea — p.ej. UUID donde
     * se esperaba Long, número malformado, etc.). Antes este caso caía al {@code
     * handleGenericException(Exception)} de abajo y se devolvía 500/INTERNAL_ERROR con el
     * mensaje crudo de Jackson — incorrecto: es 400/BAD_REQUEST. Reportado en QA Floci
     * 2026-09-10 cuando el bug del wire shape (UUID vs Long) causaba "Cannot deserialize
     * value of type java.util.UUID from String '1'" como 500 al frontend.
     *
     * <p>Se devuelve un mensaje genérico para no filtrar detalles internos de Jackson
     * (mantiene el principio S-11 de no exponer rutas internas) pero útil para que el
     * cliente sepa que el body está mal.</p>
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Payload de request malformado: {}", ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.BAD_REQUEST, "Cuerpo del request invalido o malformado");
    }

    /**
     * Parametro de query/path no convertible al tipo esperado (p.ej. {@code ?user_id=abc}
     * cuando se esperaba Long). Misma familia que {@link #handleNotReadable}: error del
     * cliente, no del servidor. Sin este handler caería al 500/INTERNAL_ERROR con el
     * stacktrace de Spring al cliente. Detectado en CI 2026-09-10 junto al fix de wire
     * shape: los 3 tests de {@code SecurityIntegrationTest} que mandaban UUID strings
     * para {@code user_id} reventaban con {@code MethodArgumentTypeMismatchException}.
     *
     * <p>El mensaje expone sólo el nombre del parámetro (no el valor crudo) para no
     * filtrar input del usuario que pudiera contener credenciales u otra info sensible.</p>
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Parametro '{}' no convertible a {}: {}", ex.getName(),
                ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "?",
                ex.getValue());
        return build(HttpStatus.BAD_REQUEST,
                "Parametro invalido: " + ex.getName());
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
