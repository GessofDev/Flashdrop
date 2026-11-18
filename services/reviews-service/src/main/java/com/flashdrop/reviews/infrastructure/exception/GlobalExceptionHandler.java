package com.flashdrop.reviews.infrastructure.exception;

import com.flashdrop.observability.error.ApiError;
import com.flashdrop.observability.tracing.TraceContext;
import com.flashdrop.reviews.domain.exception.InternalServiceUnavailableException;
import com.flashdrop.reviews.domain.exception.InvalidCommentException;
import com.flashdrop.reviews.domain.exception.InvalidOrderIdException;
import com.flashdrop.reviews.domain.exception.InvalidRatingException;
import com.flashdrop.reviews.domain.exception.InvalidTargetIdException;
import com.flashdrop.reviews.domain.exception.InvalidTargetTypeException;
import com.flashdrop.reviews.domain.exception.OrderNotDeliveredException;
import com.flashdrop.reviews.domain.exception.OrderNotFoundException;
import com.flashdrop.reviews.domain.exception.ReviewAlreadyExistsException;
import com.flashdrop.reviews.domain.exception.ReviewForbiddenException;
import com.flashdrop.reviews.domain.exception.TargetNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * Manejador global de excepciones de reviews-service.
 *
 * <p>Todas las respuestas de error usan el sobre {@link ApiError} de
 * {@code shared-observability} (mismo formato que el resto del sistema) con un
 * código HTTP estable por caso:</p>
 * <ul>
 *   <li>400 — id o payload inválido.</li>
 *   <li>403 — el rol o la relación no habilitan la calificación.</li>
 *   <li>404 — la orden o el destino no existen.</li>
 *   <li>409 — ya existe una calificación para esa terna.</li>
 *   <li>422 — la orden todavía no está entregada.</li>
 *   <li>503 — orders-service o catalog-service no están disponibles.</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String SERVICE = "reviews-service";

    // ---------------------------------------------------------------- 400

    @ExceptionHandler(InvalidRatingException.class)
    public ResponseEntity<ApiError> handleInvalidRating(InvalidRatingException ex) {
        log.warn("Calificacion invalida: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage());
    }

    @ExceptionHandler(InvalidTargetTypeException.class)
    public ResponseEntity<ApiError> handleInvalidTargetType(InvalidTargetTypeException ex) {
        log.warn("Tipo de destino invalido: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage());
    }

    @ExceptionHandler(InvalidCommentException.class)
    public ResponseEntity<ApiError> handleInvalidComment(InvalidCommentException ex) {
        log.warn("Comentario invalido: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage());
    }

    @ExceptionHandler(InvalidOrderIdException.class)
    public ResponseEntity<ApiError> handleInvalidOrderId(InvalidOrderIdException ex) {
        log.warn("Id de orden invalido: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage());
    }

    @ExceptionHandler(InvalidTargetIdException.class)
    public ResponseEntity<ApiError> handleInvalidTargetId(InvalidTargetIdException ex) {
        log.warn("Id de destino invalido: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::formatFieldError)
                .collect(Collectors.joining("; "));
        log.warn("Validacion fallida: {}", details);
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Payload malformado: {}", ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "Cuerpo del request invalido o malformado");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Parametro '{}' no convertible a {}", ex.getName(),
                ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "?");
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "Parametro invalido: " + ex.getName());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParam(MissingServletRequestParameterException ex) {
        log.warn("Parametro obligatorio ausente: {}", ex.getParameterName());
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "Parametro obligatorio: " + ex.getParameterName());
    }

    // ---------------------------------------------------------------- 403

    @ExceptionHandler(ReviewForbiddenException.class)
    public ResponseEntity<ApiError> handleForbidden(ReviewForbiddenException ex) {
        log.warn("Calificacion no autorizada: {}", ex.getMessage());
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Acceso denegado: {}", ex.getMessage());
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage());
    }

    // ---------------------------------------------------------------- 404

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ApiError> handleOrderNotFound(OrderNotFoundException ex) {
        log.warn("Orden no encontrada: {}", ex.getMessage());
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(TargetNotFoundException.class)
    public ResponseEntity<ApiError> handleTargetNotFound(TargetNotFoundException ex) {
        log.warn("Destino no encontrado: {}", ex.getMessage());
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResourceFound(NoResourceFoundException ex) {
        String message = "No handler found for "
                + (ex.getHttpMethod() != null ? ex.getHttpMethod() : "?") + " "
                + (ex.getResourcePath() != null ? ex.getResourcePath() : ex.getMessage());
        log.warn("Recurso no encontrado: {}", message);
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    // ---------------------------------------------------------------- 409

    @ExceptionHandler(ReviewAlreadyExistsException.class)
    public ResponseEntity<ApiError> handleAlreadyExists(ReviewAlreadyExistsException ex) {
        log.warn("Calificacion duplicada: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage());
    }

    /**
     * La unicidad {@code (order_id, author_user_id, target_type)} la impone la
     * base. Si dos peticiones idénticas compiten, la perdedora recibe la
     * violación de la restricción y se traduce a 409 — el reintento nunca
     * crea una fila nueva (S6).
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Violacion de integridad al guardar la calificacion: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, "CONFLICT",
                "Ya existe una calificacion para esa orden, autor y destino.");
    }

    // ---------------------------------------------------------------- 422

    @ExceptionHandler(OrderNotDeliveredException.class)
    public ResponseEntity<ApiError> handleOrderNotDelivered(OrderNotDeliveredException ex) {
        log.warn("Orden no entregada: {}", ex.getMessage());
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "UNPROCESSABLE_ENTITY", ex.getMessage());
    }

    // ---------------------------------------------------------------- 503

    @ExceptionHandler(InternalServiceUnavailableException.class)
    public ResponseEntity<ApiError> handleInternalUnavailable(InternalServiceUnavailableException ex) {
        log.error("Servicio interno no disponible: {}", ex.getMessage());
        return build(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", ex.getMessage());
    }

    // ---------------------------------------------------------------- 500

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex) {
        log.error("Error inesperado: ", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Error interno del servidor");
    }

    private static String formatFieldError(FieldError fieldError) {
        return fieldError.getField() + ": "
                + (fieldError.getDefaultMessage() != null ? fieldError.getDefaultMessage() : "invalido");
    }

    private static ResponseEntity<ApiError> build(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(
                new ApiError(code, SERVICE, TraceContext.currentTraceId(), message));
    }
}
