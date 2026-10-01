package cl.flashdrop.orders.infrastructure.api;

import cl.flashdrop.orders.domain.model.Role;
import cl.flashdrop.orders.infrastructure.adapter.outbound.IdConverter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;
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

    private static final String ROLE_PREFIX = "ROLE_";

    /**
     * @return el {@code userId} autenticado (dominio Orders, ya convertido a UUID vía
     *         {@link IdConverter#toUuid(long)}).
     * @throws AccessDeniedException si no hay una autenticación real (anónima o ausente)
     *         o si el principal no es un id numérico válido. En la práctica esto sólo puede
     *         ocurrir si el endpoint no está protegido por {@code .authenticated()} en
     *         {@code SecurityConfig} — es una defensa adicional, no el mecanismo principal.
     */
    public UUID requireCurrentUserId() {
        Authentication auth = requireAuthentication();
        try {
            long userId = Long.parseLong(auth.getName());
            return IdConverter.toUuid(userId);
        } catch (NumberFormatException e) {
            throw new AccessDeniedException("Token invalido");
        }
    }

    /**
     * PR-orders-jwt-roles (plan §4.3.0): roles del usuario autenticado, leídos de las
     * authorities {@code ROLE_<rol>} que publica {@code JwtValidationFilter}.
     *
     * @return TODOS los roles del usuario (puede tener varios); vacío si el JWT no traía
     *         el claim {@code roles} — los chequeos de rol fallan cerrado en ese caso.
     * @throws AccessDeniedException si no hay autenticación real, o si alguna authority
     *         trae un rol que no existe en {@link Role} ({@code "Rol desconocido: <x>"}).
     */
    public Set<Role> requireCurrentRoles() {
        Authentication auth = requireAuthentication();
        Set<Role> roles = EnumSet.noneOf(Role.class);
        for (GrantedAuthority authority : auth.getAuthorities()) {
            String value = authority.getAuthority();
            if (value == null || !value.startsWith(ROLE_PREFIX)) {
                continue;
            }
            String rawRole = value.substring(ROLE_PREFIX.length());
            try {
                roles.add(Role.fromClaimValue(rawRole));
            } catch (IllegalArgumentException e) {
                throw new AccessDeniedException("Rol desconocido: " + rawRole);
            }
        }
        return roles;
    }

    /**
     * @return {@code true} si el usuario autenticado tiene {@code role} entre sus roles.
     */
    public boolean hasRole(Role role) {
        return requireCurrentRoles().contains(role);
    }

    private static Authentication requireAuthentication() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("No autenticado");
        }
        return auth;
    }
}
