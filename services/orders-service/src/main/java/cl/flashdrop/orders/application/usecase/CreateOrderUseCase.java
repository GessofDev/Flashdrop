package cl.flashdrop.orders.application.usecase;

import cl.flashdrop.orders.application.command.CreateOrderCommand;
import cl.flashdrop.orders.application.dto.CreatedOrderResult;
import cl.flashdrop.orders.domain.exception.OrderDomainException;
import cl.flashdrop.orders.domain.model.*;
import cl.flashdrop.orders.domain.port.CatalogPort;
import cl.flashdrop.orders.domain.port.DeliveryPort;
import cl.flashdrop.orders.domain.port.EventPublisherPort;
import cl.flashdrop.orders.domain.port.OrderRepositoryPort;
import cl.flashdrop.orders.infrastructure.messaging.event.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Caso de uso: Crear Pedido.
 *
 * Orquesta la validación de productos (precio siempre desde Catálogo),
 * el cálculo de totales, la persistencia y la publicación del evento.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreateOrderUseCase {

    private final OrderRepositoryPort orderRepository;
    private final CatalogPort catalogPort;
    private final DeliveryPort deliveryPort;
    private final EventPublisherPort eventPublisher;

    @Value("${orders.delivery-fee:2500}")
    private BigDecimal deliveryFee;

    @Value("${orders.default-distance-km:3.2}")
    private BigDecimal defaultDistanceKm;

    @Value("${orders.default-estimated-minutes:20}")
    private int defaultEstimatedMinutes;

    @Value("${orders.rabbitmq.routing-key.order-created:order.created}")
    private String orderCreatedRoutingKey;

    @Transactional
    public CreatedOrderResult execute(CreateOrderCommand command) {
        log.info("Creando pedido para userId={}, {} ítems", command.getUserId(), command.getItems().size());

        // 1. Obtener precios actualizados desde catálogo (nunca del cliente)
        List<UUID> productIds = command.getItems().stream()
                .map(CreateOrderCommand.ItemRequest::getProductId)
                .distinct()
                .collect(Collectors.toList());

        List<ProductInfo> products = catalogPort.findProductsByIds(productIds);

        if (products.size() != productIds.size()) {
            throw new OrderDomainException("Uno o mas productos no existen");
        }

        // 2. Validar que todos los productos sean del mismo restaurante
        Order.validateSingleRestaurant(products);
        UUID restaurantId = products.get(0).getRestaurantId();

        // 3. Mapear productos por ID para lookup rápido
        Map<UUID, ProductInfo> productById = products.stream()
                .collect(Collectors.toMap(ProductInfo::getId, p -> p));

        // 4. Construir ítems con precio del catálogo
        List<OrderItem> items = command.getItems().stream().map(req -> {
            ProductInfo product = productById.get(req.getProductId());
            int qty = Math.max(1, req.getQuantity());
            BigDecimal lineTotal = OrderItem.calculateLineTotal(product.getPrice(), qty);
            return OrderItem.builder()
                    .productId(product.getId())
                    .productName(product.getName())
                    .productDescription(product.getDescription())
                    .productImage(product.getImage())
                    .quantity(qty)
                    .unitPrice(product.getPrice())
                    .lineTotal(lineTotal)
                    .build();
        }).collect(Collectors.toList());

        // 5. Resolver cliente (por userId o primer cliente disponible en modo demo)
        UUID clientId = deliveryPort.findClientIdByUserId(command.getUserId())
                .orElseThrow(() -> new OrderDomainException("No existe cliente para crear pedido"));

        // 6. Calcular totales
        BigDecimal subtotal = Order.calculateSubtotal(items);
        BigDecimal total = Order.calculateTotal(subtotal, deliveryFee);

        // 7. Construir y persistir la orden
        Order order = Order.builder()
                .clientId(clientId)
                .restaurantId(restaurantId)
                .deliveryId(null)
                .status(OrderStatus.NUEVO_PEDIDO)
                .address(command.getAddress())
                .subtotal(subtotal)
                .deliveryFee(deliveryFee)
                .total(total)
                .paymentMethod(PaymentMethod.fromValue(command.getPaymentMethod()))
                .items(items)
                .build();

        Order saved = orderRepository.save(order);

        // 8. Crear ruta de entrega
        RestaurantInfo restaurant = catalogPort.findRestaurantById(restaurantId).orElse(null);
        BigDecimal distanceKm = normalizeDistance(command.getDistanceKm());
        int estimatedMinutes = normalizeMinutes(command.getEstimatedMinutes());

        DeliveryRoute route = DeliveryRoute.builder()
                .orderId(saved.getId())
                .pickupAddress(restaurant != null
                        ? restaurant.getName() + ", " + restaurant.getAddress()
                        : "Restaurante")
                .deliveryAddress(command.getAddress())
                .distanceKm(distanceKm)
                .estimatedMinutes(estimatedMinutes)
                .status("Pendiente")
                .build();

        orderRepository.saveRoute(route);

        // 9. Publicar evento
        eventPublisher.publish(orderCreatedRoutingKey, OrderCreatedEvent.builder()
                .orderId(saved.getId())
                .clientId(clientId)
                .restaurantId(restaurantId)
                .total(total)
                .status(OrderStatus.NUEVO_PEDIDO.getValue())
                .build());

        log.info("Pedido creado con id={}, total={}", saved.getId(), total);
        return new CreatedOrderResult(saved.getId(), total);
    }

    private BigDecimal normalizeDistance(BigDecimal raw) {
        if (raw == null) return defaultDistanceKm;
        return raw.max(BigDecimal.valueOf(0.5)).min(BigDecimal.valueOf(80));
    }

    private int normalizeMinutes(Integer raw) {
        if (raw == null) return defaultEstimatedMinutes;
        return Math.min(180, Math.max(10, raw));
    }
}
