package cl.flashdrop.orders.application.usecase;

import cl.flashdrop.orders.application.dto.SalesSummary;
import cl.flashdrop.orders.domain.exception.ForbiddenOperationException;
import cl.flashdrop.orders.domain.exception.OrderDomainException;
import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.model.OrderItem;
import cl.flashdrop.orders.domain.model.OrderStatus;
import cl.flashdrop.orders.domain.model.SalesRange;
import cl.flashdrop.orders.domain.port.CatalogPort;
import cl.flashdrop.orders.domain.port.OrderRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Caso de uso: Resumen de Ventas de un Restaurante (PR-orders-metrics, spec store-flow FR-1).
 *
 * <ul>
 *   <li>Ownership: el restaurante consultado debe ser el del usuario, resuelto vía
 *       {@link CatalogPort#findRestaurantIdByUserId} (contrato C-3, sin cache) — si no, 403.
 *       El chequeo del rol Restaurante se hace en el controller.</li>
 *   <li>Solo pedidos ENTREGADO creados dentro del rango (day / week / month hacia atrás).</li>
 *   <li>Ingreso = suma de subtotales de productos (sin despacho); ticket promedio redondeado
 *       a pesos; top 5 productos por cantidad vendida (desempate: mayor ingreso).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GetRestaurantSalesSummaryUseCase {

    static final int TOP_PRODUCTS_LIMIT = 5;

    private final OrderRepositoryPort orderRepository;
    private final CatalogPort catalogPort;
    private final Clock clock;

    @Transactional(readOnly = true)
    public SalesSummary execute(UUID currentUserId, UUID restaurantId, String rawRange) {
        SalesRange range = parseRange(rawRange);

        if (!catalogPort.checkRestaurantMembership(currentUserId, restaurantId)) {
            throw new ForbiddenOperationException("No puedes consultar las ventas de otro restaurante");
        }

        OffsetDateTime to = OffsetDateTime.now(clock);
        OffsetDateTime from = to.minus(range.getLength());
        List<Order> orders = orderRepository.findByRestaurantAndStatusAndCreatedAtBetween(
                restaurantId, List.of(OrderStatus.ENTREGADO), from, to);

        log.debug("Resumen de ventas restaurante {} ({}): {} pedidos", restaurantId, range.getValue(), orders.size());
        return buildSummary(restaurantId, range, from, to, orders);
    }

    @Transactional(readOnly = true)
    public SalesSummary executeStoreSummary(UUID currentUserId, UUID restaurantId, String rawRange) {
        if (restaurantId != null) {
            return execute(currentUserId, restaurantId, rawRange);
        }
        SalesRange range = parseRange(rawRange);
        List<UUID> operableIds = catalogPort.findOperableRestaurantIdsByUserId(currentUserId);
        OffsetDateTime to = OffsetDateTime.now(clock);
        OffsetDateTime from = to.minus(range.getLength());
        Map<UUID, Order> byId = new LinkedHashMap<>();
        for (UUID id : operableIds) {
            orderRepository.findByRestaurantAndStatusAndCreatedAtBetween(
                    id, List.of(OrderStatus.ENTREGADO), from, to)
                    .forEach(o -> byId.putIfAbsent(o.getId(), o));
        }
        List<Order> orders = List.copyOf(byId.values());
        log.debug("Resumen de ventas agregado usuario {} ({} locales, {}): {} pedidos",
                currentUserId, operableIds.size(), range.getValue(), orders.size());
        return buildSummary(null, range, from, to, orders);
    }

    private static SalesSummary buildSummary(UUID restaurantId, SalesRange range,
                                             OffsetDateTime from, OffsetDateTime to, List<Order> orders) {
        BigDecimal totalRevenue = orders.stream()
                .map(Order::getSubtotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal averageTicket = orders.isEmpty()
                ? BigDecimal.ZERO
                : totalRevenue.divide(BigDecimal.valueOf(orders.size()), 0, RoundingMode.HALF_UP);

        return new SalesSummary(restaurantId, range.getValue(), from, to,
                orders.size(), totalRevenue, averageTicket, topProducts(orders));
    }

    private static SalesRange parseRange(String rawRange) {
        try {
            return SalesRange.fromValue(rawRange);
        } catch (IllegalArgumentException e) {
            throw new OrderDomainException("Rango invalido: " + rawRange + ". Valores validos: day, week, month");
        }
    }

    private static List<SalesSummary.TopProduct> topProducts(List<Order> orders) {
        Map<UUID, ProductTotals> byProduct = new LinkedHashMap<>();
        for (Order order : orders) {
            if (order.getItems() == null) {
                continue;
            }
            for (OrderItem item : order.getItems()) {
                byProduct.computeIfAbsent(item.getProductId(), id -> new ProductTotals()).add(item);
            }
        }
        return byProduct.entrySet().stream()
                .map(e -> new SalesSummary.TopProduct(e.getKey(), e.getValue().name,
                        e.getValue().quantity, e.getValue().revenue))
                .sorted(Comparator.comparingLong(SalesSummary.TopProduct::quantitySold).reversed()
                        .thenComparing(SalesSummary.TopProduct::revenue, Comparator.reverseOrder()))
                .limit(TOP_PRODUCTS_LIMIT)
                .toList();
    }

    /** Acumulador por producto; el nombre es el snapshot guardado en el pedido. */
    private static final class ProductTotals {
        private String name;
        private long quantity;
        private BigDecimal revenue = BigDecimal.ZERO;

        void add(OrderItem item) {
            if (name == null) {
                name = item.getProductName();
            }
            quantity += item.getQuantity();
            if (item.getLineTotal() != null) {
                revenue = revenue.add(item.getLineTotal());
            }
        }
    }
}
