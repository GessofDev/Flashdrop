package cl.flashdrop.orders.infrastructure.api;

import cl.flashdrop.orders.application.service.OrderPolicyResolver;
import cl.flashdrop.orders.domain.model.OrderPolicy;
import cl.flashdrop.orders.domain.port.OrderPolicyRepositoryPort;
import cl.flashdrop.orders.infrastructure.api.dto.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Plazos globales de los pedidos (F7 y F8). Solo el administrador (ver {@code AdminAccess}).
 *
 * <ul>
 *   <li>{@code storeResponseMinutes}: cuanto tiene la tienda para aceptar un pedido pagado. Si no responde,
 *       el pedido se cancela. Es el mismo para todos los locales: una tienda ya no lo elige.</li>
 *   <li>{@code courierWaitMinutes}: cuanto se espera un repartidor desde que la tienda acepto. Pasado el plazo
 *       sin repartidor, el pedido se cancela.</li>
 *   <li>{@code paymentExpirationMinutes} y {@code customerCancelWindowMinutes}: los demas plazos globales.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin/order-policy")
@RequiredArgsConstructor
public class AdminOrderPolicyController {

    public record PolicyBody(
            @NotNull @Min(OrderPolicy.MIN_MINUTES) @Max(OrderPolicy.MAX_MINUTES) Integer paymentExpirationMinutes,
            @NotNull @Min(OrderPolicy.MIN_MINUTES) @Max(OrderPolicy.MAX_MINUTES) Integer storeResponseMinutes,
            @NotNull @Min(0) @Max(OrderPolicy.MAX_MINUTES) Integer customerCancelWindowMinutes,
            @NotNull @Min(OrderPolicy.MIN_MINUTES) @Max(OrderPolicy.MAX_MINUTES) Integer courierWaitMinutes) {
    }

    private final OrderPolicyRepositoryPort policies;
    private final OrderPolicyResolver policyResolver;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping
    public ApiResponse<PolicyBody> get() {
        currentUserResolver.requireAdminUserId();
        return ApiResponse.success(toBody(policyResolver.effectiveFor(null)));
    }

    @PutMapping
    public ApiResponse<PolicyBody> replace(@Valid @RequestBody PolicyBody body) {
        UUID adminId = currentUserResolver.requireAdminUserId();
        policies.save(OrderPolicy.builder()
                .paymentExpirationMinutes(body.paymentExpirationMinutes())
                .storeResponseMinutes(body.storeResponseMinutes())
                .customerCancelWindowMinutes(body.customerCancelWindowMinutes())
                .courierWaitMinutes(body.courierWaitMinutes())
                .build(), adminId);
        return ApiResponse.success("Plazos actualizados", toBody(policyResolver.effectiveFor(null)));
    }

    private static PolicyBody toBody(OrderPolicy.Effective e) {
        return new PolicyBody(e.paymentExpirationMinutes(), e.storeResponseMinutes(),
                e.customerCancelWindowMinutes(), e.courierWaitMinutes());
    }
}
