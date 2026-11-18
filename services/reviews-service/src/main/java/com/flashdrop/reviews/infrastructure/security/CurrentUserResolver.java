package com.flashdrop.reviews.infrastructure.security;

import com.flashdrop.reviews.domain.model.AuthorRole;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/**
 * Resuelve la identidad del usuario autenticado a partir del
 * {@link SecurityContextHolder}.
 *
 * <p>Espejo de {@code delivery-service/infrastructure/security/CurrentUserResolver},
 * adaptado al dominio de reviews: el claim {@code roles} se traduce al enum
 * {@link AuthorRole} (Cliente | Repartidor | Restaurante).</p>
 *
 * <p>El claim es <b>multi-rol</b>: {@link #requireCurrentRoles()} devuelve
 * <b>todos</b> los roles del usuario, nunca solo el primero. La decisión de
 * "con qué rol se autoriza" la toma la política de dominio, no este resolver.</p>
 *
 * <p>Política de fallas:
 * <ul>
 *   <li>{@link #requireCurrentUserId()} y {@link #requireCurrentRoles()} lanzan
 *       {@link AccessDeniedException} cuando no hay autenticación real.</li>
 *   <li>{@link #hasRole(AuthorRole)} devuelve {@code false} en lugar de lanzar,
 *       para chequeos blandos.</li>
 * </ul>
 */
@Component
public class CurrentUserResolver {

    private static final String ROLE_PREFIX = "ROLE_";

    /**
     * @return el {@code userId} autenticado como {@code Long}, parseado del
     *         subject del JWT.
     * @throws AccessDeniedException si no hay autenticación real o si el
     *         principal no es un id numérico válido.
     */
    public Long requireCurrentUserId() {
        Authentication auth = requireAuthentication();
        try {
            return Long.parseLong(auth.getName());
        } catch (NumberFormatException | NullPointerException e) {
            throw new AccessDeniedException("Token invalido");
        }
    }

    /**
     * @return TODOS los roles del usuario autenticado; vacío si el JWT no
     *         traía el claim {@code roles}.
     * @throws AccessDeniedException si no hay autenticación real, o si alguna
     *         authority trae un rol que no existe en {@link AuthorRole}.
     */
    public Set<AuthorRole> requireCurrentRoles() {
        Authentication auth = requireAuthentication();
        Set<AuthorRole> roles = EnumSet.noneOf(AuthorRole.class);
        for (GrantedAuthority authority : auth.getAuthorities()) {
            String value = authority.getAuthority();
            if (value == null || !value.startsWith(ROLE_PREFIX)) {
                continue;
            }
            String rawRole = value.substring(ROLE_PREFIX.length());
            try {
                roles.add(AuthorRole.fromClaimValue(rawRole));
            } catch (IllegalArgumentException e) {
                throw new AccessDeniedException("Rol desconocido: " + rawRole);
            }
        }
        return roles;
    }

    /**
     * @return {@code true} si el usuario autenticado tiene ese rol. Fail-soft:
     *         devuelve {@code false} si no hay autenticación o si el rol no
     *         está presente. NO lanza.
     */
    public boolean hasRole(AuthorRole role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return false;
        }
        try {
            return requireCurrentRoles().contains(role);
        } catch (AccessDeniedException e) {
            // Rol desconocido en las authorities: se trata como "sin rol" en vez
            // de romper al caller. El camino estricto es requireCurrentRoles().
            return false;
        }
    }

    private static Authentication requireAuthentication() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("No autenticado");
        }
        return auth;
    }
}
