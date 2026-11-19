package cl.flashdrop.orders.application.usecase;

import cl.flashdrop.orders.application.command.CreateOrderCommand;
import cl.flashdrop.orders.application.dto.CreatedOrderResult;
import cl.flashdrop.orders.application.service.DeliveryFeeCalculator;
import cl.flashdrop.orders.application.service.OrderPolicyResolver;
import cl.flashdrop.orders.domain.exception.OrderDomainException;
import cl.flashdrop.orders.domain.model.*;
import cl.flashdrop.orders.domain.port.CatalogPort;
import cl.flashdrop.orders.domain.port.ClientPort;
import cl.flashdrop.orders.domain.port.DeliveryCodePort;
import cl.flashdrop.orders.domain.port.OrderRepositoryPort;
import cl.flashdrop.orders.domain.port.PaymentGatewayPort;
import cl.flashdrop.orders.domain.port.PaymentRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Caso de uso: Crear Pedido.
 *
 * Orquesta la validación de productos (precio siempre desde Catálogo), el cálculo del envío y de los
 * totales, y la persistencia. El pedido nace en "Pendiente de pago" con su cobro abierto en la
 * pasarela: la ruta de entrega y el evento {@code order.created} se generan recién cuando el pago se
 * aprueba ({@link ConfirmPaymentUseCase}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreateOrderUseCase {

    static final int MAX_IDEMPOTENCY_KEY_LENGTH = 64;
    private static final String CURRENCY = "CLP";

    private final OrderRepositoryPort orderRepository;
    private final CatalogPort catalogPort;
    private final ClientPort clientPort;
    private final DeliveryCodePort deliveryCodePort;
    private final DeliveryFeeCalculator deliveryFeeCalculator;
    private final PaymentRepositoryPort paymentRepository;
    private final PaymentGatewayPort paymentGateway;
    private final OrderPolicyResolver policyResolver;
    private final Clock clock;

    @Value("${orders.default-distance-km:3.2}")
    private BigDecimal defaultDistanceKm;

    @Value("${orders.default-estimated-minutes:20}")
    private int defaultEstimatedMinutes;

    @Transactional
    public CreatedOrderResult execute(CreateOrderCommand command) {
        log.info("Creando pedido para userId={}, {} ítems", command.getUserId(), command.getItems().size());

        if (command.getReceptionMode() != ReceptionMode.SEGURO) {
            throw new OrderDomainException("Todos los pedidos requieren recepción SEGURO con PIN");
        }

        // Solo se cobra con Tarjeta: Efectivo y Transferencia se rechazan antes de tocar nada.
        PaymentMethod paymentMethod = PaymentMethod.fromRequest(command.getPaymentMethod());

        String idempotencyKey = normalizeIdempotencyKey(command.getIdempotencyKey());

        // 0. El modo SEGURO necesita la clave del código: si falta se rechaza ANTES de guardar
        // nada, con un 4xx (un 5xx dejaría una ruta huérfana en Delivery y el gateway abre el
        // circuit breaker de /api/orders tras varios 5xx seguidos).
        if (command.getReceptionMode() == ReceptionMode.SEGURO && !deliveryCodePort.isSecureModeAvailable()) {
            throw new OrderDomainException(
                    "La entrega SEGURO con PIN no está disponible temporalmente; no se puede crear el pedido");
        }

        // 1. Obtener precios actualizados desde catálogo (nunca del cliente)
        List<UUID> productIds = command.getItems().stream()
                .map(CreateOrderCommand.ItemRequest::getProductId)
                .distinct()
                .collect(Collectors.toList());

        List<ProductInfo> products = catalogPort.findProductsByIds(productIds);

        if (products.size() != productIds.size()) {
            throw new OrderDomainException("Uno o mas productos no existen");
        }

        // 1b. Rechazar el pedido completo si algún producto existe pero no está disponible
        boolean hayProductoNoDisponible = products.stream().anyMatch(product -> !product.isAvailable());
        if (hayProductoNoDisponible) {
            throw new OrderDomainException("Uno o mas productos no estan disponibles");
        }

        // 2. Validar que todos los productos sean del mismo restaurante
        Order.validateSingleRestaurant(products);
        UUID restaurantId = products.get(0).getRestaurantId();

        // 2b. Con el local cerrado se rechaza antes de guardar nada. Si Catalog no conoce el
        // local se conserva el comportamiento de siempre: el pedido sigue con dirección genérica.
        RestaurantInfo restaurant = catalogPort.findRestaurantById(restaurantId).orElse(null);
        if (restaurant != null && !restaurant.isOpen()) {
            throw new OrderDomainException("El local está cerrado en este momento");
        }

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

        // 5. Resolver cliente a partir del userId autenticado (ver OrderController /
        // CurrentUserResolver — GAP-04). No existe fallback: si el usuario no tiene un
        // perfil de cliente en la tabla propia `client`, la creación falla explícitamente.
        // Primera compra: si el token trae el rol Cliente y aún no hay perfil, se crea aquí
        // (acuerdo con Auth: Auth no interviene en el registro).
        UUID clientId = clientPort.findClientIdByUserId(command.getUserId())
                .orElseGet(() -> {
                    if (!command.isClientProfileAllowed()) {
                        throw new OrderDomainException("No existe cliente para crear pedido");
                    }
                    return clientPort.findOrCreateClientIdByUserId(command.getUserId());
                });

        // 5b. Reintento de la misma peticion: devuelve el pedido ya creado en vez de duplicarlo.
        if (idempotencyKey != null) {
            Order existing = orderRepository.findByClientIdAndIdempotencyKey(clientId, idempotencyKey).orElse(null);
            if (existing != null) {
                log.info("Pedido {} ya existia para la llave de idempotencia; se devuelve el mismo", existing.getId());
                return replay(existing);
            }
        }

        // 6. Calcular totales. El envio lo calcula el servidor con la tarifa del administrador
        // (nunca viene del cliente) y se cobra antes de pagar.
        BigDecimal subtotal = Order.calculateSubtotal(items);
        List<DeliveryFeeCalculator.Line> feeLines = command.getItems().stream()
                .map(req -> new DeliveryFeeCalculator.Line(productById.get(req.getProductId()), req.getQuantity()))
                .collect(Collectors.toList());
        DeliveryQuote quote = deliveryFeeCalculator.calculate(feeLines, restaurant,
                command.getDeliveryLatitude(), command.getDeliveryLongitude());
        BigDecimal deliveryFee = quote.deliveryFee();
        BigDecimal total = Order.calculateTotal(subtotal, deliveryFee);

        // Con coordenadas y tarifa, la distancia es la calculada; si no, la que mande la app o la de siempre.
        BigDecimal distanceKm = quote.distanceKm() != null
                ? quote.distanceKm().max(BigDecimal.valueOf(0.5)).min(BigDecimal.valueOf(80))
                : normalizeDistance(command.getDistanceKm());
        int estimatedMinutes = normalizeMinutes(command.getEstimatedMinutes());

        // 7. Construir y persistir la orden, pendiente de pago
        OffsetDateTime now = OffsetDateTime.now(clock);
        OrderPolicy.Effective policy = policyResolver.effectiveFor(restaurantId);
        Order order = Order.builder()
                .clientId(clientId)
                .restaurantId(restaurantId)
                .deliveryId(null)
                .status(OrderStatus.PENDIENTE_PAGO)
                .address(command.getAddress())
                .commune(normalizeCommune(command.getCommune()))
                .deliveryLatitude(command.getDeliveryLatitude())
                .deliveryLongitude(command.getDeliveryLongitude())
                .distanceKm(distanceKm)
                .estimatedMinutes(estimatedMinutes)
                .subtotal(subtotal)
                .deliveryFee(deliveryFee)
                .total(total)
                .paymentMethod(paymentMethod)
                .receptionMode(command.getReceptionMode())
                .idempotencyKey(idempotencyKey)
                .paymentExpiresAt(now.plusMinutes(policy.paymentExpirationMinutes()))
                .items(items)
                .build();

        Order saved = orderRepository.save(order);

        // El codigo se deriva del pedido ya persistido.
        String deliveryCode = saved.isSecureReception() ? deliveryCodePort.currentCodeFor(saved) : null;

        // 8. Abrir el cobro en la pasarela. El monto es el calculado por el servidor.
        PaymentGatewayPort.PaymentSession session = paymentGateway.createSession(saved.getId(), total, CURRENCY);
        Payment payment = paymentRepository.save(Payment.open(
                saved.getId(), paymentGateway.providerName(), session.providerTxId(), total, now));

        log.info("Pedido creado con id={}, total={}, pendiente de pago", saved.getId(), total);
        return new CreatedOrderResult(saved.getId(), total, saved.getReceptionMode().name(), deliveryCode,
                OrderStatus.PENDIENTE_PAGO.getValue(), deliveryFee, payment.getId(), session.paymentUrl(),
                saved.getPaymentExpiresAt());
    }

    /** Respuesta para un reintento: el mismo pedido, con su cobro abierto si lo hay. */
    private CreatedOrderResult replay(Order existing) {
        Payment open = paymentRepository.findByOrderId(existing.getId()).stream()
                .filter(p -> p.getStatus() == PaymentStatus.PENDIENTE).findFirst().orElse(null);
        String deliveryCode = existing.isSecureReception() ? deliveryCodePort.currentCodeFor(existing) : null;
        return new CreatedOrderResult(existing.getId(), existing.getTotal(), existing.getReceptionMode().name(),
                deliveryCode, existing.getStatus().getValue(), existing.getDeliveryFee(),
                open == null ? null : open.getId(),
                open == null ? null : paymentGateway.paymentUrlFor(open.getProviderTxId()),
                existing.getPaymentExpiresAt());
    }

    private String normalizeIdempotencyKey(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String key = raw.trim();
        if (key.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new OrderDomainException("La llave de idempotencia no puede exceder "
                    + MAX_IDEMPOTENCY_KEY_LENGTH + " caracteres");
        }
        return key;
    }

    private BigDecimal normalizeDistance(BigDecimal raw) {
        if (raw == null) return defaultDistanceKm;
        return raw.max(BigDecimal.valueOf(0.5)).min(BigDecimal.valueOf(80));
    }

    private int normalizeMinutes(Integer raw) {
        if (raw == null) return defaultEstimatedMinutes;
        return Math.min(180, Math.max(10, raw));
    }

    private String normalizeCommune(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
