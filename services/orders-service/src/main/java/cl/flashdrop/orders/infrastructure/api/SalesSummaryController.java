package cl.flashdrop.orders.infrastructure.api;

import cl.flashdrop.orders.application.dto.SalesSummary;
import cl.flashdrop.orders.application.usecase.GetRestaurantSalesSummaryUseCase;
import cl.flashdrop.orders.domain.model.Role;
import cl.flashdrop.orders.infrastructure.adapter.outbound.IdConverter;
import cl.flashdrop.orders.infrastructure.api.dto.response.ApiResponse;
import cl.flashdrop.orders.infrastructure.api.dto.response.SalesSummaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Resumen de ventas de la tienda (PR-orders-metrics, spec store-flow FR-1, tasks T-27).
 *
 * <p>Solo rol Restaurante (403). El ownership del restaurante (403 si el {@code id} del path
 * no es el del usuario) lo valida el use case vía Catalog. Wire: {@code id} Long (lo emite
 * catalog-service); dominio: UUID — conversión al límite con {@link IdConverter}, igual que
 * el resto de los endpoints (T-27b).</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class SalesSummaryController {

    private final GetRestaurantSalesSummaryUseCase getRestaurantSalesSummaryUseCase;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping("/restaurants/{id}/sales-summary")
    public ApiResponse<SalesSummaryResponse> getSalesSummary(
            @PathVariable("id") Long restaurantIdLong,
            @RequestParam(value = "range", defaultValue = "week") String range) {
        log.debug("GET /api/orders/restaurants/{}/sales-summary, range={}", restaurantIdLong, range);
        if (!currentUserResolver.hasRole(Role.RESTAURANTE)) {
            throw new AccessDeniedException("Solo los restaurantes pueden consultar sus ventas");
        }
        UUID currentUserId = currentUserResolver.requireCurrentUserId();
        SalesSummary summary = getRestaurantSalesSummaryUseCase.execute(
                currentUserId, IdConverter.toUuid(restaurantIdLong), range);
        return ApiResponse.success(toResponse(summary));
    }

    private static SalesSummaryResponse toResponse(SalesSummary summary) {
        return SalesSummaryResponse.builder()
                .restaurantId(IdConverter.toLong(summary.restaurantId()))
                .range(summary.range())
                .from(summary.from())
                .to(summary.to())
                .totalOrders(summary.totalOrders())
                .totalRevenue(summary.totalRevenue())
                .averageTicket(summary.averageTicket())
                .topProducts(summary.topProducts().stream()
                        .map(p -> SalesSummaryResponse.TopProductDto.builder()
                                .productId(p.productId())
                                .productName(p.productName())
                                .quantitySold(p.quantitySold())
                                .revenue(p.revenue())
                                .build())
                        .toList())
                .build();
    }
}
