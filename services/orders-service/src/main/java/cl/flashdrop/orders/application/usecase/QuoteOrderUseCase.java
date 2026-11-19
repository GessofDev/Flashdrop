package cl.flashdrop.orders.application.usecase;

import cl.flashdrop.orders.application.service.DeliveryFeeCalculator;
import cl.flashdrop.orders.domain.exception.OrderDomainException;
import cl.flashdrop.orders.domain.model.DeliveryQuote;
import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.model.OrderItem;
import cl.flashdrop.orders.domain.model.ProductInfo;
import cl.flashdrop.orders.domain.model.RestaurantInfo;
import cl.flashdrop.orders.domain.port.CatalogPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Caso de uso: cotizar un pedido antes de pagar (F8). Devuelve el subtotal, el envio y el total
 * que el servidor cobrara, con el mismo calculo que usa {@link CreateOrderUseCase}.
 */
@Service
@RequiredArgsConstructor
public class QuoteOrderUseCase {

    public record Item(UUID productId, int quantity) {
    }

    public record OrderQuote(BigDecimal subtotal, DeliveryQuote delivery, BigDecimal total) {
    }

    private final CatalogPort catalogPort;
    private final DeliveryFeeCalculator deliveryFeeCalculator;

    public OrderQuote execute(List<Item> items, BigDecimal destinationLatitude, BigDecimal destinationLongitude) {
        if (items == null || items.isEmpty() || items.stream().anyMatch(i -> i.productId() == null)) {
            throw new OrderDomainException("El pedido debe contener al menos un producto");
        }
        List<UUID> productIds = items.stream().map(Item::productId).distinct().collect(Collectors.toList());
        List<ProductInfo> products = catalogPort.findProductsByIds(productIds);
        if (products.size() != productIds.size()) {
            throw new OrderDomainException("Uno o mas productos no existen");
        }
        if (products.stream().anyMatch(p -> !p.isAvailable())) {
            throw new OrderDomainException("Uno o mas productos no estan disponibles");
        }
        Order.validateSingleRestaurant(products);
        RestaurantInfo restaurant = catalogPort.findRestaurantById(products.get(0).getRestaurantId()).orElse(null);

        Map<UUID, ProductInfo> byId = products.stream().collect(Collectors.toMap(ProductInfo::getId, p -> p));
        List<OrderItem> orderItems = items.stream().map(i -> {
            ProductInfo product = byId.get(i.productId());
            int qty = Math.max(1, i.quantity());
            return OrderItem.builder().productId(product.getId()).quantity(qty).unitPrice(product.getPrice())
                    .lineTotal(OrderItem.calculateLineTotal(product.getPrice(), qty)).build();
        }).collect(Collectors.toList());
        BigDecimal subtotal = Order.calculateSubtotal(orderItems);

        List<DeliveryFeeCalculator.Line> lines = items.stream()
                .map(i -> new DeliveryFeeCalculator.Line(byId.get(i.productId()), i.quantity()))
                .collect(Collectors.toList());
        DeliveryQuote delivery = deliveryFeeCalculator.calculate(lines, restaurant,
                destinationLatitude, destinationLongitude);
        return new OrderQuote(subtotal, delivery, Order.calculateTotal(subtotal, delivery.deliveryFee()));
    }
}
