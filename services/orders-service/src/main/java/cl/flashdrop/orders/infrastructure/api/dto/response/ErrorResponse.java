package cl.flashdrop.orders.infrastructure.api.dto.response;

import lombok.Builder;

/**
 * DTO de respuesta de error conforme al contrato estándar de {@code MIGRATION_PLAN.md} §10
 * ("Códigos de error estándar"): {@code { "status": 404, "error": "NOT_FOUND", "message": "..." } }.
 *
 * <p>{@code error} usa los códigos constantes de esa tabla (BAD_REQUEST, UNAUTHORIZED,
 * FORBIDDEN, NOT_FOUND, CONFLICT, VALIDATION_ERROR, INTERNAL_ERROR, SERVICE_UNAVAILABLE),
 * no la frase de razón HTTP (ej. "Not Found").</p>
 */
@Builder
public record ErrorResponse(int status, String error, String message) {
}
