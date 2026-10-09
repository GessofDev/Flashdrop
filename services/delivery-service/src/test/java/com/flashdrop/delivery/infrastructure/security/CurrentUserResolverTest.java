package com.flashdrop.delivery.infrastructure.security;

import com.flashdrop.delivery.domain.model.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link CurrentUserResolver}.
 *
 * <p>Drives the resolver directly by mutating the {@link SecurityContextHolder}
 * with a known {@link UsernamePasswordAuthenticationToken} — no Spring context,
 * no servlet container. That keeps the test focused on the resolver's own
 * contract and runs in milliseconds.
 *
 * <p>The resolver is the single boundary through which controllers and
 * use cases read identity from the security context. Everything else in
 * delivery-service that needs the userId or the roles goes through these
 * three methods, so the tests are the contract.
 */
@DisplayName("CurrentUserResolver — identity from SecurityContextHolder")
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

    // -------------------------------------------------------------------------
    // Test helpers
    // -------------------------------------------------------------------------

    private static void installAuth(String principal, List<GrantedAuthority> authorities) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, authorities));
        SecurityContextHolder.setContext(context);
    }

    private static AnonymousAuthenticationToken anonymous() {
        return new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
    }

    // -------------------------------------------------------------------------
    // requireCurrentUserId
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("requireCurrentUserId")
    class RequireCurrentUserId {

        @Test
        @DisplayName("TC1: principal is '42' → returns 42L (Long, not UUID — delivery uses raw Long subject)")
        void numericSubjectReturnsLong() {
            installAuth("42", List.of());
            assertThat(resolver.requireCurrentUserId()).isEqualTo(42L);
        }

        @Test
        @DisplayName("TC2: no authentication → AccessDeniedException ('No autenticado')")
        void noAuthThrows() {
            assertThatThrownBy(resolver::requireCurrentUserId)
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("No autenticado");
        }

        @Test
        @DisplayName("TC3: anonymous principal → AccessDeniedException (defence in depth)")
        void anonymousAuthThrows() {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(anonymous());
            SecurityContextHolder.setContext(context);

            assertThatThrownBy(resolver::requireCurrentUserId)
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("TC4: principal is not a Long ('abc') → AccessDeniedException (defence in depth)")
        void nonNumericSubjectThrows() {
            // The JwtAuthenticationFilter only ever sets a Long subject. If we
            // ever see a non-numeric principal here, something is wrong upstream
            // (token issuance or filter wiring) — fail loud, not silent.
            installAuth("abc", List.of());
            assertThatThrownBy(resolver::requireCurrentUserId)
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Token invalido");
        }

        @Test
        @DisplayName("TC5: principal is null (broken auth object) → AccessDeniedException")
        void nullPrincipalThrows() {
            installAuth(null, List.of());
            assertThatThrownBy(resolver::requireCurrentUserId)
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    // -------------------------------------------------------------------------
    // requireCurrentRoles
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("requireCurrentRoles")
    class RequireCurrentRoles {

        @Test
        @DisplayName("TC1: no authentication → AccessDeniedException")
        void noAuthThrows() {
            assertThatThrownBy(resolver::requireCurrentRoles)
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("No autenticado");
        }

        @Test
        @DisplayName("TC2: empty authorities → empty roles (fail-closed for hasRole checks)")
        void emptyAuthoritiesReturnsEmptySet() {
            installAuth("42", List.of());
            assertThat(resolver.requireCurrentRoles()).isEmpty();
        }

        @Test
        @DisplayName("TC3: ROLE_Repartidor → Set(Role.REPARTIDOR)")
        void singleRepartidorRoleMaps() {
            installAuth("42", List.of(new SimpleGrantedAuthority("ROLE_Repartidor")));
            assertThat(resolver.requireCurrentRoles()).containsExactly(Role.REPARTIDOR);
        }

        @Test
        @DisplayName("TC4: all three demo roles → Set with all three enums (order-independent)")
        void allThreeRolesMap() {
            installAuth("42", List.of(
                    new SimpleGrantedAuthority("ROLE_Cliente"),
                    new SimpleGrantedAuthority("ROLE_Restaurante"),
                    new SimpleGrantedAuthority("ROLE_Repartidor")));
            assertThat(resolver.requireCurrentRoles())
                    .containsExactlyInAnyOrder(Role.CLIENTE, Role.RESTAURANTE, Role.REPARTIDOR);
        }

        @Test
        @DisplayName("TC5: unknown role (ROLE_Admin) → AccessDeniedException ('Rol desconocido: Admin')")
        void unknownRoleThrows() {
            // The resolver is the right place to fail loud on an unknown role:
            // it means auth-service changed its role names and delivery-service
            // wasn't updated. Better to deny than to silently treat as no-role.
            installAuth("42", List.of(new SimpleGrantedAuthority("ROLE_Admin")));
            assertThatThrownBy(resolver::requireCurrentRoles)
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Rol desconocido")
                    .hasMessageContaining("Admin");
        }

        @Test
        @DisplayName("TC6: authority without ROLE_ prefix (e.g. SCOPE_read) is ignored, not treated as unknown")
        void nonPrefixedAuthorityIsIgnored() {
            // Spring Security uses the ROLE_ prefix for hasRole() matching but
            // can carry other authorities (SCOPE_, etc.). The resolver only
            // looks at ROLE_ authorities; others are silently ignored.
            installAuth("42", List.of(
                    new SimpleGrantedAuthority("SCOPE_read"),
                    new SimpleGrantedAuthority("ROLE_Repartidor")));
            assertThat(resolver.requireCurrentRoles()).containsExactly(Role.REPARTIDOR);
        }
    }

    // -------------------------------------------------------------------------
    // hasRole
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("hasRole")
    class HasRole {

        @Test
        @DisplayName("TC1: ROLE_Repartidor → hasRole(REPARTIDOR) = true")
        void repartidorHasRepartidor() {
            installAuth("42", List.of(new SimpleGrantedAuthority("ROLE_Repartidor")));
            assertThat(resolver.hasRole(Role.REPARTIDOR)).isTrue();
        }

        @Test
        @DisplayName("TC2: ROLE_Cliente → hasRole(REPARTIDOR) = false")
        void clienteDoesNotHaveRepartidor() {
            installAuth("42", List.of(new SimpleGrantedAuthority("ROLE_Cliente")));
            assertThat(resolver.hasRole(Role.REPARTIDOR)).isFalse();
        }

        @Test
        @DisplayName("TC3: empty authorities → hasRole returns false for any role (does NOT throw)")
        void emptyAuthoritiesDoesNotHaveAnyRole() {
            installAuth("42", List.of());
            // hasRole is the read-side convenience: it must never throw, so
            // controllers can use it for soft checks (e.g. feature flag by
            // role) without try/catch.
            assertThat(resolver.hasRole(Role.REPARTIDOR)).isFalse();
            assertThat(resolver.hasRole(Role.CLIENTE)).isFalse();
            assertThat(resolver.hasRole(Role.RESTAURANTE)).isFalse();
        }

        @Test
        @DisplayName("TC4: no auth → hasRole returns false (does NOT throw)")
        void noAuthDoesNotHaveAnyRole() {
            // Same reason as TC3: hasRole is fail-soft, requireCurrentRoles
            // is fail-loud. Callers pick.
            assertThat(resolver.hasRole(Role.REPARTIDOR)).isFalse();
        }
    }

    // -------------------------------------------------------------------------
    // Round-trip property
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Round-trip with JwtAuthenticationFilter output")
    class RoundTrip {

        @Test
        @DisplayName("TC1: a token with roles=['Repartidor'] → resolver sees REPARTIDOR (filter → resolver contract)")
        void endToEndRoleExtraction() {
            // Simulates exactly what JwtAuthenticationFilter would set after
            // validating a JWT with roles=['Repartidor']: principal='42',
            // authorities=[ROLE_Repartidor]. The resolver should expose it
            // as Role.REPARTIDOR and the userId as 42L.
            installAuth("42", List.of(new SimpleGrantedAuthority("ROLE_Repartidor")));
            assertThat(resolver.requireCurrentUserId()).isEqualTo(42L);
            assertThat(resolver.requireCurrentRoles()).containsExactly(Role.REPARTIDOR);
            assertThat(resolver.hasRole(Role.REPARTIDOR)).isTrue();
            assertThat(resolver.hasRole(Role.CLIENTE)).isFalse();
        }
    }
}
