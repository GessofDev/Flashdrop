package cl.flashdrop.orders.infrastructure.api;

import cl.flashdrop.orders.domain.model.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PR-orders-jwt-roles (plan §4.3.0): {@link CurrentUserResolver} expone los roles que
 * {@link cl.flashdrop.orders.config.JwtValidationFilter} publica como authorities
 * {@code ROLE_<rol>}. Un usuario puede tener varios roles (acuerdo con Auth), por lo que
 * se resuelve el conjunto completo y no "el primero".
 */
class CurrentUserResolverTest {

    private CurrentUserResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new CurrentUserResolver();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static void autenticarCon(String... authorities) {
        List<SimpleGrantedAuthority> granted = Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("42", null, granted));
    }

    @Test
    void requireCurrentRoles_conUnRol_retornaEseRol() {
        autenticarCon("ROLE_Restaurante");

        assertEquals(Set.of(Role.RESTAURANTE), resolver.requireCurrentRoles());
    }

    @Test
    void requireCurrentRoles_conVariosRoles_retornaTodos() {
        autenticarCon("ROLE_Cliente", "ROLE_Restaurante", "ROLE_Repartidor");

        assertEquals(Set.of(Role.CLIENTE, Role.RESTAURANTE, Role.REPARTIDOR), resolver.requireCurrentRoles());
    }

    /** Un JWT sin claim {@code roles} autentica igual, pero sin roles: los chequeos fallan cerrado. */
    @Test
    void requireCurrentRoles_sinAuthorities_retornaConjuntoVacio() {
        autenticarCon();

        assertTrue(resolver.requireCurrentRoles().isEmpty());
    }

    @Test
    void requireCurrentRoles_sinAutenticacion_lanzaAccessDenied() {
        assertThrows(AccessDeniedException.class, () -> resolver.requireCurrentRoles());
    }

    @Test
    void requireCurrentRoles_conRolDesconocido_lanzaAccessDeniedConMensaje() {
        autenticarCon("ROLE_Admin");

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> resolver.requireCurrentRoles());
        assertEquals("Rol desconocido: Admin", ex.getMessage());
    }

    @Test
    void hasRole_conRolPresente_retornaTrue() {
        autenticarCon("ROLE_Cliente", "ROLE_Repartidor");

        assertTrue(resolver.hasRole(Role.REPARTIDOR));
    }

    @Test
    void hasRole_conRolAusente_retornaFalse() {
        autenticarCon("ROLE_Cliente");

        assertFalse(resolver.hasRole(Role.RESTAURANTE));
    }
}
