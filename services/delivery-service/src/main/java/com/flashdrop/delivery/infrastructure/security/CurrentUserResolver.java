package com.flashdrop.delivery.infrastructure.security;

import com.flashdrop.delivery.domain.model.Role;
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
 * {@link SecurityContextHolder} (espejo de
 * {@code orders-service/.../infrastructure/api/CurrentUserResolver.java},
 * adaptado al contrato de delivery-service que trabaja con {@code Long}
 * userId directamente, sin conversión a UUID).
 *
 * <p>{@link JwtAuthenticationFilter} valida el JWT contra auth-service y
 * coloca el {@code userId} (Long, tal como lo emite auth-service en el
 * subject del token) como principal de la autenticación, junto con las
 * authorities {@code ROLE_<rol>} extraídas del claim {@code roles} del
 * payload. Este componente centraliza la lectura de esos dos valores para
 * que controllers y casos de uso tengan una sola fuente de verdad y un
 * solo punto donde se decide el comportamiento fail-closed.
 *
 * <p>Política de fallas:
 * <ul>
 *   <li>{@link #requireCurrentUserId()} y {@link #requireCurrentRoles()}
 *       lanzan {@link AccessDeniedException} cuando no hay autenticación
 *       real (anónima o ausente) — son las versiones "estrictas" para
 *       endpoints que no tienen sentido sin identidad.</li>
 *   <li>{@link #hasRole(Role)} devuelve {@code false} en lugar de
 *       lanzar, para que los callers puedan hacer chequeos blandos
 *       (e.g. feature flag por rol) sin try/catch.</li>
 * </ul>
 */
@Component
public class CurrentUserResolver {

    private static final String ROLE_PREFIX = "ROLE_";

    /**
     * @return el {@code userId} autenticado como {@code Long}, parseado del
     *         subject del JWT.
     * @throws AccessDeniedException si no hay autenticación real (anónima o
     *         ausente) o si el principal no es un id numérico válido. El
     *         segundo caso solo puede ocurrir si el endpoint no está
     *         protegido por {@code .authenticated()} en
     *         {@code SecurityConfig} — es defensa en profundidad, no el
     *         mecanismo principal.
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
     * @return TODOS los roles del usuario autenticado (puede tener varios);
     *         vacío si el JWT no traía el claim {@code roles} — los chequeos
     *         de rol fallan cerrado en ese caso vía {@code hasRole(...) → false}
     *         o vía {@code requireCurrentRoles()} cuando se llama con
     *         authorities que no matchean ningún rol conocido.
     * @throws AccessDeniedException si no hay autenticación real, o si alguna
     *         authority trae un rol que no existe en {@link Role}
     *         (e.g. auth-service agregó un rol nuevo y delivery-service no
     *         se actualizó — preferimos deny antes que ignorar).
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
     * @return {@code true} si el usuario autenticado tiene {@code role} entre
     *         sus roles. Fail-soft: devuelve {@code false} si no hay
     *         autenticación o si el rol no está presente. NO lanza, para que
     *         los callers puedan hacer branching por rol sin try/catch.
     */
    public boolean hasRole(Role role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return false;
        }
        Set<Role> roles;
        try {
            roles = requireCurrentRoles();
        } catch (AccessDeniedException e) {
            // Unknown role in the JWT authorities — treat as "no role" rather
            // than crashing the caller. The strict path (requireCurrentRoles)
            // is the one that throws; hasRole is the lenient convenience.
            return false;
        }
        return roles.contains(role);
    }

    private static Authentication requireAuthentication() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("No autenticado");
        }
        return auth;
    }
}
