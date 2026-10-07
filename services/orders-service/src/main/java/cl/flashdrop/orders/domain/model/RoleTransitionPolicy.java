package cl.flashdrop.orders.domain.model;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Qué estados de pedido puede fijar cada rol (PR-orders-status-authz, spec FR-4, ADR-2).
 *
 * <p>La matriz vive en código (no en configuración) para que cambiar permisos pase por
 * code review. Se combina con {@link Order#validateStatusTransition}: el rol decide a qué
 * estado puede llevar el pedido (si no → 403) y el dominio decide si se puede llegar desde
 * el estado actual (si no → 409). La combinación reproduce la tabla rol → transición del
 * spec — ver {@code RoleTransitionPolicyTest}.</p>
 *
 * <p>EN_CAMINO es legacy: ningún rol puede llevar un pedido a ese estado.</p>
 */
public final class RoleTransitionPolicy {

    private static final Map<Role, Set<OrderStatus>> SETTABLE_STATUSES = Map.of(
            Role.RESTAURANTE, EnumSet.of(OrderStatus.PREPARANDO, OrderStatus.LISTO_PARA_RETIRO),
            Role.REPARTIDOR, EnumSet.of(OrderStatus.RETIRADO, OrderStatus.ENTREGADO),
            Role.CLIENTE, EnumSet.noneOf(OrderStatus.class)
    );

    private RoleTransitionPolicy() {
    }

    /**
     * @return los roles del usuario que le permiten fijar {@code target}. Un usuario puede
     *         tener varios roles; basta con que uno lo permita.
     */
    public static Set<Role> rolesAllowedToSet(Set<Role> userRoles, OrderStatus target) {
        Set<Role> granting = EnumSet.noneOf(Role.class);
        for (Role role : userRoles) {
            if (SETTABLE_STATUSES.getOrDefault(role, Set.of()).contains(target)) {
                granting.add(role);
            }
        }
        return granting;
    }

    public static boolean canSet(Set<Role> userRoles, OrderStatus target) {
        return !rolesAllowedToSet(userRoles, target).isEmpty();
    }
}
