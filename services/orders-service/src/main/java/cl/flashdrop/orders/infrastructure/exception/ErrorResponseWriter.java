package cl.flashdrop.orders.infrastructure.exception;

import cl.flashdrop.orders.infrastructure.api.dto.response.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Escribe el body {@code { status, error, message }} (MIGRATION_PLAN.md §10) en respuestas
 * de error generadas fuera de Spring MVC — filtros de servlet ({@code InternalApiKeyFilter},
 * {@code JwtValidationFilter}) y los handlers de {@code SecurityConfig} — donde
 * {@link GlobalExceptionHandler} no llega a intervenir.
 */
public final class ErrorResponseWriter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ErrorResponseWriter() {
    }

    public static void write(HttpServletResponse response, int status, String error, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(MAPPER.writeValueAsString(new ErrorResponse(status, error, message)));
    }
}
