package com.flashdrop.delivery.infrastructure.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps the {@code roles} claim emitted by auth-service in the JWT payload
 * (e.g. {@code ["Cliente", "Restaurante", "Repartidor"]}) onto Spring
 * Security {@code ROLE_<rol>} authorities.
 *
 * <p>Used by {@link JwtAuthenticationFilter} when constructing the
 * {@code UsernamePasswordAuthenticationToken} so that {@code hasRole(…)}
 * checks in {@code SecurityConfig} and method-level role queries via
 * {@code CurrentUserResolver} can match the courier's role.
 *
 * <p><b>Pure mapping, no policy.</b> This class does not validate the
 * incoming role strings against a known set — that is the job of the
 * downstream resolver (and ultimately the security matcher). Unknown
 * roles are passed through with the {@code ROLE_} prefix. {@code null}
 * and blank entries are skipped so a single malformed entry cannot
 * cause a 500 in the auth filter chain.
 */
public final class RoleAuthoritiesExtractor {

    private static final String ROLE_PREFIX = "ROLE_";

    private RoleAuthoritiesExtractor() {
        // utility class
    }

    /**
     * @param roles the values of the {@code roles} claim, or {@code null} when
     *              the claim is absent. A {@code null} or empty input returns
     *              an empty list — fail-closed: a token without a role cannot
     *              satisfy any {@code hasRole(…)} check downstream.
     * @return Spring Security authorities with the {@code ROLE_} prefix. Order
     *         matches the input order. {@code null} and blank entries are
     *         skipped without throwing.
     */
    public static List<GrantedAuthority> fromClaim(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }
        List<GrantedAuthority> result = new ArrayList<>(roles.size());
        for (String role : roles) {
            if (role == null || role.isBlank()) {
                continue;
            }
            result.add(new SimpleGrantedAuthority(ROLE_PREFIX + role));
        }
        return result;
    }
}
