package cl.flashdrop.orders.infrastructure.api.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/**
 * DTO de respuesta de error conforme al contrato definido en openapi.yaml.
 *
 * Estructura: {@code { "error": "Bad Request", "message": "...", "timestamp": "..." }}
 * Reemplaza el uso de {@link ApiResponse} en casos de error para cumplir el contrato.
 */
@Getter
@Builder
public class ErrorResponse {

    /** Tipo de error HTTP (ej. "Bad Request", "Not Found") */
    private final String error;

    /** Descripción del error para el consumidor de la API */
    private final String message;

    /** Timestamp de cuando ocurrió el error */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private final OffsetDateTime timestamp;
}
