package cl.flashdrop.orders.domain.model;

import cl.flashdrop.orders.domain.exception.OrderDomainException;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PR-orders-status-authz (spec FR-4, ADR-2): qué estados puede fijar cada rol.
 */
class RoleTransitionPolicyTest {

    @Test
    void shouldLetRestauranteSetOnlyKitchenStatuses() {
        Set<Role> roles = Set.of(Role.RESTAURANTE);

        assertTrue(RoleTransitionPolicy.canSet(roles, OrderStatus.PREPARANDO));
        assertTrue(RoleTransitionPolicy.canSet(roles, OrderStatus.LISTO_PARA_RETIRO));
        assertFalse(RoleTransitionPolicy.canSet(roles, OrderStatus.RETIRADO));
        assertFalse(RoleTransitionPolicy.canSet(roles, OrderStatus.ENTREGADO));
    }

    @Test
    void shouldLetRepartidorSetOnlyDeliveryStatuses() {
        Set<Role> roles = Set.of(Role.REPARTIDOR);

        assertTrue(RoleTransitionPolicy.canSet(roles, OrderStatus.RETIRADO));
        assertTrue(RoleTransitionPolicy.canSet(roles, OrderStatus.ENTREGADO));
        assertFalse(RoleTransitionPolicy.canSet(roles, OrderStatus.PREPARANDO));
        assertFalse(RoleTransitionPolicy.canSet(roles, OrderStatus.LISTO_PARA_RETIRO));
    }

    @Test
    void shouldNotLetClienteSetAnyStatus() {
        for (OrderStatus target : OrderStatus.values()) {
            assertFalse(RoleTransitionPolicy.canSet(Set.of(Role.CLIENTE), target), target.name());
        }
    }

    /** EN_CAMINO es legacy (deprecated): ningún rol puede llevar un pedido a ese estado. */
    @Test
    void shouldNotLetAnyRoleSetLegacyEnCamino() {
        assertFalse(RoleTransitionPolicy.canSet(Set.of(Role.values()), OrderStatus.EN_CAMINO));
    }

    @Test
    void shouldNotLetUserWithoutRolesSetAnyStatus() {
        assertFalse(RoleTransitionPolicy.canSet(Set.of(), OrderStatus.PREPARANDO));
    }

    /** Usuario multirol (ej. admin@demo.cl): basta con que uno de sus roles lo permita. */
    @Test
    void shouldReturnOnlyTheRolesThatGrantTheTarget() {
        Set<Role> all = Set.of(Role.CLIENTE, Role.RESTAURANTE, Role.REPARTIDOR);

        assertEquals(Set.of(Role.RESTAURANTE), RoleTransitionPolicy.rolesAllowedToSet(all, OrderStatus.PREPARANDO));
        assertEquals(Set.of(Role.REPARTIDOR), RoleTransitionPolicy.rolesAllowedToSet(all, OrderStatus.ENTREGADO));
    }

    /**
     * Contrato combinado: "el rol puede fijar el destino" + "el dominio permite pasar desde
     * el estado actual" reproduce EXACTAMENTE la tabla rol → transición del spec FR-4, más:
     * <ul>
     *   <li>{@code NUEVO_PEDIDO → LISTO_PARA_RETIRO} para Restaurante (el spec lo define como
     *       el atajo "si la tienda decide saltarse PREPARANDO");</li>
     *   <li>{@code EN_CAMINO → RETIRADO/ENTREGADO} para Repartidor (pedidos legacy que quedaron
     *       en EN_CAMINO deben poder terminarse — plan §5 y riesgo #17).</li>
     * </ul>
     */
    @Test
    void shouldMatchSpecRoleTransitionTableWhenCombinedWithDomainMatrix() {
        Set<List<Object>> expected = Set.of(
                List.of(Role.REPARTIDOR, OrderStatus.LISTO_PARA_RETIRO, OrderStatus.RETIRADO),
                List.of(Role.REPARTIDOR, OrderStatus.RETIRADO, OrderStatus.ENTREGADO),
                List.of(Role.REPARTIDOR, OrderStatus.EN_CAMINO, OrderStatus.RETIRADO),
                List.of(Role.REPARTIDOR, OrderStatus.EN_CAMINO, OrderStatus.ENTREGADO),
                List.of(Role.RESTAURANTE, OrderStatus.NUEVO_PEDIDO, OrderStatus.PREPARANDO),
                List.of(Role.RESTAURANTE, OrderStatus.PREPARANDO, OrderStatus.LISTO_PARA_RETIRO),
                List.of(Role.RESTAURANTE, OrderStatus.NUEVO_PEDIDO, OrderStatus.LISTO_PARA_RETIRO)
        );

        Set<List<Object>> actual = new HashSet<>();
        for (Role role : Role.values()) {
            for (OrderStatus from : OrderStatus.values()) {
                for (OrderStatus to : OrderStatus.values()) {
                    if (RoleTransitionPolicy.canSet(Set.of(role), to) && domainAllows(from, to)) {
                        actual.add(List.of(role, from, to));
                    }
                }
            }
        }

        assertEquals(expected, actual);
    }

    private static boolean domainAllows(OrderStatus from, OrderStatus to) {
        try {
            Order.builder().status(from).build().validateStatusTransition(to);
            return true;
        } catch (OrderDomainException e) {
            return false;
        }
    }
}
