package com.flashdrop.delivery.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link Role}.
 *
 * <p>The role string values are coordinated with the seed of auth-service
 * ({@code services/auth-service/src/main/resources/db/seed/V2__seed_development.sql})
 * and the JWT issuance path in
 * {@code com.flashdrop.auth.infrastructure.adapter.outbound.security.JwtTokenService#issue},
 * which writes the claim as
 * {@code .claim("roles", claims.roles())} where each value is the
 * {@code roles.name} column from auth's {@code roles} table.
 *
 * <p>Keeping these in sync is critical: a typo here would cause a real JWT
 * to map to "rol desconocido" and the courier to be denied at the
 * delivery-service boundary with no actionable error.
 */
@DisplayName("Role — claim string ↔ enum mapping")
class RoleTest {

    @Nested
    @DisplayName("fromClaimValue")
    class FromClaimValue {

        @Test
        @DisplayName("TC1: 'Cliente' → Role.CLIENTE")
        void clienteMapsToCliente() {
            assertThat(Role.fromClaimValue("Cliente")).isEqualTo(Role.CLIENTE);
        }

        @Test
        @DisplayName("TC2: 'Restaurante' → Role.RESTAURANTE")
        void restauranteMapsToRestaurante() {
            assertThat(Role.fromClaimValue("Restaurante")).isEqualTo(Role.RESTAURANTE);
        }

        @Test
        @DisplayName("TC3: 'Repartidor' → Role.REPARTIDOR")
        void repartidorMapsToRepartidor() {
            assertThat(Role.fromClaimValue("Repartidor")).isEqualTo(Role.REPARTIDOR);
        }

        @Test
        @DisplayName("TC4: unknown value → IllegalArgumentException with the bad value in the message")
        void unknownValueThrows() {
            assertThatThrownBy(() -> Role.fromClaimValue("Admin"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Admin");
        }

        @Test
        @DisplayName("TC5: null → IllegalArgumentException (defensive — the resolver never calls this with null)")
        void nullValueThrows() {
            assertThatThrownBy(() -> Role.fromClaimValue(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("TC6: lowercase 'cliente' → IllegalArgumentException (claim values are case-sensitive, match auth-service exactly)")
        void wrongCaseDoesNotMatch() {
            // This is intentional. auth-service writes the role name as it is in
            // the DB; if a future change lower-cases it, this test will fail
            // loudly, which is the right behavior — the resolver should never
            // silently coerce roles.
            assertThatThrownBy(() -> Role.fromClaimValue("cliente"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("claimValue accessor")
    class ClaimValueAccessor {

        @Test
        @DisplayName("TC1: every enum value's claimValue is non-blank (used to build ROLE_<rol> authorities)")
        void everyClaimValueIsNonBlank() {
            for (Role role : Role.values()) {
                assertThat(role.claimValue())
                        .as("role %s must have a non-blank claimValue", role.name())
                        .isNotBlank();
            }
        }

        @Test
        @DisplayName("TC2: claimValue is the same string that fromClaimValue accepts (round-trip property)")
        void claimValueIsInverseOfFromClaimValue() {
            for (Role role : Role.values()) {
                assertThat(Role.fromClaimValue(role.claimValue())).isEqualTo(role);
            }
        }
    }
}
