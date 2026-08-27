package cl.flashdrop.orders.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filtro de autenticación interna.
 *
 * <p>Protege {@code /api/internal/**} validando la cabecera {@code X-Internal-Api-Key}
 * contra la clave configurada en {@code internal.api.key}. Los endpoints públicos
 * (todo lo que no comience con {@code /api/internal/}) no se ven afectados.</p>
 */
@Slf4j
@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    static final String INTERNAL_PATH_PREFIX = "/api/internal/";
    static final String API_KEY_HEADER = "X-Internal-Api-Key";

    private final String apiKey;

    public InternalApiKeyFilter(@Value("${internal.api.key}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getServletPath();
        if (!path.startsWith(INTERNAL_PATH_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String provided = request.getHeader(API_KEY_HEADER);
        if (provided == null || !provided.equals(apiKey)) {
            log.warn("Acceso denegado a {} - X-Internal-Api-Key invalida o ausente", path);
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
