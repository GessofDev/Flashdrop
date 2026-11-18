package com.flashdrop.reviews.domain.policy;

import com.flashdrop.reviews.domain.model.AuthorRole;
import com.flashdrop.reviews.domain.valueobjects.RatingTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Matriz de autorización completa (S4, spec §4).
 *
 * <pre>
 * Autor \ Destino | TIENDA | REPARTIDOR | CLIENTE
 * CLIENTE         |   sí   |     sí     |   no
 * REPARTIDOR      |   sí   |     no     |   sí
 * RESTAURANTE     |   no   |     sí     |   sí
 * </pre>
 */
@DisplayName("RatingPolicy — matriz de autorización §4 (6 relaciones)")
class RatingPolicyTest {

    @Nested
    @DisplayName("Relaciones permitidas")
    class Allowed {

        @Test
        @DisplayName("TC1: CLIENTE puede calificar TIENDA y REPARTIDOR, no CLIENTE")
        void cliente_matrix() {
            assertThat(RatingPolicy.allows(AuthorRole.CLIENTE, RatingTarget.TIENDA)).isTrue();
            assertThat(RatingPolicy.allows(AuthorRole.CLIENTE, RatingTarget.REPARTIDOR)).isTrue();
            assertThat(RatingPolicy.allows(AuthorRole.CLIENTE, RatingTarget.CLIENTE)).isFalse();
        }

        @Test
        @DisplayName("TC2: REPARTIDOR puede calificar TIENDA y CLIENTE, no REPARTIDOR")
        void repartidor_matrix() {
            assertThat(RatingPolicy.allows(AuthorRole.REPARTIDOR, RatingTarget.TIENDA)).isTrue();
            assertThat(RatingPolicy.allows(AuthorRole.REPARTIDOR, RatingTarget.CLIENTE)).isTrue();
            assertThat(RatingPolicy.allows(AuthorRole.REPARTIDOR, RatingTarget.REPARTIDOR)).isFalse();
        }

        @Test
        @DisplayName("TC3: RESTAURANTE puede calificar REPARTIDOR y CLIENTE, no TIENDA")
        void restaurante_matrix() {
            assertThat(RatingPolicy.allows(AuthorRole.RESTAURANTE, RatingTarget.REPARTIDOR)).isTrue();
            assertThat(RatingPolicy.allows(AuthorRole.RESTAURANTE, RatingTarget.CLIENTE)).isTrue();
            assertThat(RatingPolicy.allows(AuthorRole.RESTAURANTE, RatingTarget.TIENDA)).isFalse();
        }

        @Test
        @DisplayName("TC4: la matriz tiene exactamente 6 relaciones permitidas de 9 posibles")
        void matrix_hasSixAllowedRelations() {
            long allowed = 0;
            for (AuthorRole role : AuthorRole.values()) {
                for (RatingTarget target : RatingTarget.values()) {
                    if (RatingPolicy.allows(role, target)) {
                        allowed++;
                    }
                }
            }
            assertThat(allowed).isEqualTo(6L);
        }
    }

    @Nested
    @DisplayName("Destinos permitidos por rol")
    class AllowedTargets {

        @Test
        @DisplayName("TC5: allowedTargets(CLIENTE) = {TIENDA, REPARTIDOR}")
        void cliente_targets() {
            assertThat(RatingPolicy.allowedTargets(AuthorRole.CLIENTE))
                    .containsExactlyInAnyOrder(RatingTarget.TIENDA, RatingTarget.REPARTIDOR);
        }

        @Test
        @DisplayName("TC6: allowedTargets(REPARTIDOR) = {TIENDA, CLIENTE}")
        void repartidor_targets() {
            assertThat(RatingPolicy.allowedTargets(AuthorRole.REPARTIDOR))
                    .containsExactlyInAnyOrder(RatingTarget.TIENDA, RatingTarget.CLIENTE);
        }

        @Test
        @DisplayName("TC7: allowedTargets(RESTAURANTE) = {REPARTIDOR, CLIENTE}")
        void restaurante_targets() {
            assertThat(RatingPolicy.allowedTargets(AuthorRole.RESTAURANTE))
                    .containsExactlyInAnyOrder(RatingTarget.REPARTIDOR, RatingTarget.CLIENTE);
        }

        @Test
        @DisplayName("TC8: el conjunto es inmutable")
        void allowedTargets_isImmutable() {
            Set<RatingTarget> targets = RatingPolicy.allowedTargets(AuthorRole.CLIENTE);
            assertThat(targets).isNotEmpty();
            assertThat(targets).isEqualTo(EnumSet.copyOf(targets));
        }
    }

    @Nested
    @DisplayName("Roles del claim")
    class ClaimRoles {

        @Test
        @DisplayName("TC9: los claim values son los que emite auth-service")
        void claimValues() {
            assertThat(AuthorRole.CLIENTE.claimValue()).isEqualTo("Cliente");
            assertThat(AuthorRole.REPARTIDOR.claimValue()).isEqualTo("Repartidor");
            assertThat(AuthorRole.RESTAURANTE.claimValue()).isEqualTo("Restaurante");
        }

        @Test
        @DisplayName("TC10: fromClaimValue es case-sensitive y rechaza lo desconocido")
        void fromClaimValue_isStrict() {
            assertThat(AuthorRole.fromClaimValue("Cliente")).isEqualTo(AuthorRole.CLIENTE);
            assertThat(AuthorRole.fromClaimValue("Repartidor")).isEqualTo(AuthorRole.REPARTIDOR);
            assertThat(AuthorRole.fromClaimValue("Restaurante")).isEqualTo(AuthorRole.RESTAURANTE);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> AuthorRole.fromClaimValue("cliente"))
                    .isInstanceOf(IllegalArgumentException.class);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> AuthorRole.fromClaimValue("Admin"))
                    .isInstanceOf(IllegalArgumentException.class);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> AuthorRole.fromClaimValue(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
