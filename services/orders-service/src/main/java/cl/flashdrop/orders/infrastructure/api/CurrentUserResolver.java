package cl.flashdrop.orders.infrastructure.api;

import cl.flashdrop.orders.infrastructure.adapter.outbound.IdConverter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Resuelve la identidad del usuario autenticado a partir del {@link SecurityContextHolder}
 * (GAP-04, auditoría 2026-09-04).
 *
 * <p>{@link cl.flashdrop.orders.config.JwtValidationFilter} valida el JWT contra Auth y
 * coloca el {@code userId} (Long, tal como lo emite Auth en el subject del token — ver
 * MIGRATION_PLAN.md, el {@code sub} nunca es un UUID) como principal de la autenticación.
 * Este componente centraliza la extracción de ese valor y su conversión al {@code UUID}
 * que usa el dominio de Orders, reutilizando el mismo {@link IdConverter} que ya usa todo
 * el resto del servicio — sin inventar un esquema de conversión nuevo.</p>
 */
@Component
public class CurrentUserResolver {

    /**
     * @return el {@code userId} autenticado (dominio Orders, ya convertido a UUID vía
     *         {@link IdConverter#toUuid(long)}).
     * @throws AccessDeniedException si no hay una autenticación real (anónima o ausente)
     *         o si el principal no es un id numérico válido. En la práctica esto sólo puede
     *         ocurrir si el endpoint no está protegido por {@code .authenticated()} en
     *         {@code SecurityConfig} — es una defensa adicional, no el mecanismo principal.
     */
    public UUID requireCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("No autenticado");
        }
        try {
            long userId = Long.parseLong(auth.getName());
            return IdConverter.toUuid(userId);
        } catch (NumberFormatException e) {
            throw new AccessDeniedException("Token invalido");
        }
    }
}
