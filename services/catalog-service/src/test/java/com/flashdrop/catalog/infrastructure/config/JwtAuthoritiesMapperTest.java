package com.flashdrop.catalog.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Plan de pruebas §2.3: mapeo del claim {@code roles[]} del JWT a authorities de Spring
 * Security ({@code ROLE_Restaurante}, {@code ROLE_Cliente}), con manejo de token sin claims
 * o con roles vacíos.
 *
 * <p>La lógica vive en {@link SecurityConfig#jwtAuthenticationConverter()}; se prueba en
 * aislamiento, sin levantar el contexto de Spring: {@code SecurityConfig} se instancia
 * directamente y el JWT se arma a mano (sin firma ni JWKS).</p>
 */
class JwtAuthoritiesMapperTest {

    private final JwtAuthenticationConverter converter =
            new SecurityConfig(new ObjectMapper(), "http://localhost/jwks", "flashdrop-auth")
                    .jwtAuthenticationConverter();

    private Jwt jwtWith(Map<String, Object> claims) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("4")
                .claims(target -> target.putAll(claims))
                .build();
    }

    private List<String> authoritiesOf(Jwt jwt) {
        return converter.convert(jwt).getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }

    @Test
    void rolesClaim_mapsEachRoleToARolePrefixedAuthority() {
        Jwt jwt = jwtWith(Map.of("roles", List.of("Restaurante", "Cliente")));

        assertThat(authoritiesOf(jwt)).containsExactlyInAnyOrder("ROLE_Restaurante", "ROLE_Cliente");
    }

    @Test
    void tokenWithoutRolesClaim_hasNoAuthorities() {
        Jwt jwt = jwtWith(Map.of("iss", "flashdrop-auth"));

        assertThat(authoritiesOf(jwt)).isEmpty();
    }

    @Test
    void emptyRolesList_hasNoAuthorities() {
        Jwt jwt = jwtWith(Map.of("roles", List.of()));

        assertThat(authoritiesOf(jwt)).isEmpty();
    }
}
