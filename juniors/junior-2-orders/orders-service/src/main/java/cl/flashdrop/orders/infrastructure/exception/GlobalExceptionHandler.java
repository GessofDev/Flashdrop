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

import java.time.OffsetDateTime;
import java.util.stream.Collectors;

/**
 * Manejador global de excepciones para orders-service.
 *
 * Retorna la estructura exacta definida en openapi.yaml:
 * {@code { "error": "...", "message": "...", "timestamp": "..." }}
 * con el código HTTP apropiado para coincidir con el comportamiento del backend Node.js.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OrderDomainException.class)
    public ResponseEntity<ErrorResponse> handleDomainException(OrderDomainException ex) {
        log.warn("Excepción de dominio de pedido capturada: {}", ex.getMessage());
        String msg = ex.getMessage();
        HttpStatus status = HttpStatus.BAD_REQUEST;

        // Mapeo dinámico de estados HTTP para coincidir con Node.js
        if (msg.contains("no encontrado") || msg.contains("no existen") || msg.contains("no disponibles")) {
            status = HttpStatus.NOT_FOUND;
        } else if (msg.contains("perfil de repartidor")) {
            status = HttpStatus.FORBIDDEN;
        } else if (msg.contains("ya tienes") || msg.contains("ya fueron tomados") || msg.contains("Alguien tomo")) {
            status = HttpStatus.CONFLICT;
        }

        return ResponseEntity.status(status).body(
            ErrorResponse.builder()
                .error(status.getReasonPhrase())
                .message(msg)
                .timestamp(OffsetDateTime.now())
                .build()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("Error de validación de argumentos REST: {}", details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse.builder()
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message(details)
                .timestamp(OffsetDateTime.now())
                .build()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Error no controlado capturado: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ErrorResponse.builder()
                .error(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase())
                .message("Error interno del servidor: " + ex.getMessage())
                .timestamp(OffsetDateTime.now())
                .build()
        );
    }
}

