package com.flashdrop.delivery.infrastructure.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RoleAuthoritiesExtractor}.
 *
 * <p>Verifies the contract used by {@link JwtAuthenticationFilter} to map the
 * {@code roles} claim emitted by auth-service ({@code ["Cliente", "Restaurante",
 * "Repartidor"]}) onto Spring Security {@code ROLE_<rol>} authorities. The
 * filter calls into this helper; the helper is the boundary that can be tested
 * without signing keys, JWKS, or the servlet container.
 */
@DisplayName("RoleAuthoritiesExtractor — claim 'roles' → ROLE_ authorities")
class RoleAuthoritiesExtractorTest {

    @Nested
    @DisplayName("Null or empty claim")
    class NullOrEmpty {

        @Test
        @DisplayName("TC1: null claim → empty authorities (fail-closed: no role matches downstream)")
        void nullClaim_returnsEmptyAuthorities() {
            List<GrantedAuthority> result = RoleAuthoritiesExtractor.fromClaim(null);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("TC2: empty list → empty authorities")
        void emptyList_returnsEmptyAuthorities() {
            List<GrantedAuthority> result = RoleAuthoritiesExtractor.fromClaim(List.of());
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("Known roles (Cliente, Restaurante, Repartidor)")
    class KnownRoles {

        @Test
        @DisplayName("TC1: single Repartidor → exactly one ROLE_Repartidor authority")
        void singleRepartidor_mapsToRoleRepartidor() {
            List<GrantedAuthority> result = RoleAuthoritiesExtractor.fromClaim(List.of("Repartidor"));
            assertThat(result).containsExactly(new SimpleGrantedAuthority("ROLE_Repartidor"));
        }

        @Test
        @DisplayName("TC2: all three demo roles → three ROLE_<rol> authorities, order preserved")
        void allThreeRoles_mapToAllThreeAuthorities() {
            List<GrantedAuthority> result = RoleAuthoritiesExtractor.fromClaim(
                    List.of("Cliente", "Restaurante", "Repartidor"));
            assertThat(result).containsExactly(
                    new SimpleGrantedAuthority("ROLE_Cliente"),
                    new SimpleGrantedAuthority("ROLE_Restaurante"),
                    new SimpleGrantedAuthority("ROLE_Repartidor"));
        }

        @Test
        @DisplayName("TC3: order is preserved (a user with Cliente+Repartidor is a real edge case)")
        void orderPreservedForMultipleRoles() {
            List<GrantedAuthority> result = RoleAuthoritiesExtractor.fromClaim(
                    List.of("Cliente", "Repartidor"));
            assertThat(result).containsExactly(
                    new SimpleGrantedAuthority("ROLE_Cliente"),
                    new SimpleGrantedAuthority("ROLE_Repartidor"));
        }
    }

    @Nested
    @DisplayName("Unknown or malformed entries")
    class UnknownOrMalformedEntries {

        @Test
        @DisplayName("TC1: unknown role (e.g. 'Admin') — passed through as ROLE_Admin; validation is downstream")
        void unknownRole_passedThroughAsRolePrefixed() {
            // The extractor does not validate against a known enum. The downstream
            // CurrentUserResolver is the right place to reject unknown roles. This
            // keeps the extractor a pure mapping with no policy.
            List<GrantedAuthority> result = RoleAuthoritiesExtractor.fromClaim(List.of("Admin"));
            assertThat(result).containsExactly(new SimpleGrantedAuthority("ROLE_Admin"));
        }

        @Test
        @DisplayName("TC2: null entry inside the list — skipped, not NPE")
        void nullEntryInsideList_isSkipped() {
            List<GrantedAuthority> result = RoleAuthoritiesExtractor.fromClaim(
                    java.util.Arrays.asList("Repartidor", null, "Cliente"));
            assertThat(result).containsExactly(
                    new SimpleGrantedAuthority("ROLE_Repartidor"),
                    new SimpleGrantedAuthority("ROLE_Cliente"));
        }

        @Test
        @DisplayName("TC3: blank entry — skipped")
        void blankEntryInsideList_isSkipped() {
            List<GrantedAuthority> result = RoleAuthoritiesExtractor.fromClaim(
                    java.util.Arrays.asList("Repartidor", "", "  "));
            assertThat(result).containsExactly(new SimpleGrantedAuthority("ROLE_Repartidor"));
        }
    }
}
