package cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa;

import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.model.OrderItem;
import cl.flashdrop.orders.domain.model.OrderStatus;
import cl.flashdrop.orders.domain.model.PaymentMethod;
import cl.flashdrop.orders.domain.port.OrderRepositoryPort;
import cl.flashdrop.orders.infrastructure.adapter.outbound.IdConverter;
import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.entity.OrderEntity;
import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.entity.OrderItemEntity;
import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.repository.SpringDataOrderItemRepository;
import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.repository.SpringDataOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Repository
@Profile({"postgres", "default"})
@RequiredArgsConstructor
public class JpaOrderRepositoryAdapter implements OrderRepositoryPort {

    private final SpringDataOrderRepository orderRepository;
    private final SpringDataOrderItemRepository orderItemRepository;

    @Override
    @Transactional
    public Order save(Order order) {
        log.debug("JpaOrderRepositoryAdapter: guardando pedido");
        OrderEntity orderEntity = OrderEntity.builder()
                .id(order.getId() != null ? IdConverter.toLong(order.getId()) : null)
                .clientId(IdConverter.toLong(order.getClientId()))
                .restaurantId(IdConverter.toLong(order.getRestaurantId()))
                .deliveryId(order.getDeliveryId() != null ? IdConverter.toLong(order.getDeliveryId()) : null)
                .status(order.getStatus().getValue())
                .address(order.getAddress())
                .subtotal(order.getSubtotal())
                .deliveryFee(order.getDeliveryFee())
                .total(order.getTotal())
                .paymentMethod(order.getPaymentMethod().getValue())
                .createdAt(order.getCreatedAt() != null ? order.getCreatedAt() : OffsetDateTime.now())
                .build();

        OrderEntity savedOrder = orderRepository.save(orderEntity);
        Long orderId = savedOrder.getId();

        List<OrderItemEntity> itemEntities = new ArrayList<>();
        if (order.getItems() != null) {
            for (OrderItem item : order.getItems()) {
                OrderItemEntity itemEntity = OrderItemEntity.builder()
                        .orderId(orderId)
                        .productId(IdConverter.toLong(item.getProductId()))
                        .productName(item.getProductName())
                        .productDescription(item.getProductDescription())
                        .productImage(item.getProductImage())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .total(item.getLineTotal())
                        .build();
                itemEntities.add(itemEntity);
            }
        }
        List<OrderItemEntity> savedItems = orderItemRepository.saveAll(itemEntities);
        return mapToOrder(savedOrder, savedItems);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findById(UUID id) {
        if (id == null) return Optional.empty();
        long rawId = IdConverter.toLong(id);
        log.debug("JpaOrderRepositoryAdapter: buscando pedido id={}", rawId);
        Optional<OrderEntity> orderOpt = orderRepository.findById(rawId);
        if (orderOpt.isEmpty()) return Optional.empty();

        List<OrderItemEntity> items = orderItemRepository.findByOrderId(rawId);
        return Optional.of(mapToOrder(orderOpt.get(), items));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findAll(UUID restaurantId) {
        List<OrderEntity> orders;
        if (restaurantId != null) {
            long rawRestId = IdConverter.toLong(restaurantId);
            orders = orderRepository.findByRestaurantId(rawRestId);
        } else {
            orders = orderRepository.findAll();
        }
        return mapWithItems(orders);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findByClientId(UUID clientId) {
        if (clientId == null) return List.of();
        return mapWithItems(orderRepository.findByClientId(IdConverter.toLong(clientId)));
    }

    @Override
    @Transactional
    public void updateStatus(UUID orderId, OrderStatus status) {
        if (orderId == null || status == null) return;
        long rawId = IdConverter.toLong(orderId);
        orderRepository.findById(rawId).ifPresent(entity -> {
            entity.setStatus(status.getValue());
            orderRepository.save(entity);
        });
    }

    @Override
    @Transactional
    public int claimOrders(List<UUID> orderIds, UUID deliveryId) {
        if (orderIds == null || orderIds.isEmpty() || deliveryId == null) {
            return 0;
        }
        List<Long> rawIds = orderIds.stream().map(IdConverter::toLong).collect(Collectors.toList());
        Long rawDeliveryId = IdConverter.toLong(deliveryId);

        // Una sola sentencia condicional (delivery_id IS NULL): atómica frente a dos repartidores
        // simultáneos. Si devuelve menos que lo pedido, el use case aborta y revierte.
        return orderRepository.claimUnassigned(rawDeliveryId, rawIds);
    }

    @Override
    @Transactional(readOnly = true)
    public int countActiveOrdersByDelivery(UUID deliveryId) {
        if (deliveryId == null) return 0;
        long rawDeliveryId = IdConverter.toLong(deliveryId);
        List<String> activeStatuses = List.of(
                OrderStatus.LISTO_PARA_RETIRO.getValue(),
                OrderStatus.EN_CAMINO.getValue(),
                OrderStatus.RETIRADO.getValue()
        );
        return (int) orderRepository.countByDeliveryIdAndStatusIn(rawDeliveryId, activeStatuses);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findByIdsForClaim(List<UUID> orderIds) {
        return findByIds(orderIds);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findByIds(List<UUID> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) return List.of();
        List<Long> rawIds = orderIds.stream().map(IdConverter::toLong).collect(Collectors.toList());
        return mapWithItems(orderRepository.findByIdIn(rawIds));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findAvailableForDelivery(UUID restaurantId, int limit) {
        if (restaurantId == null || limit < 1) return List.of();
        List<OrderEntity> orders = orderRepository.findByRestaurantIdAndStatusAndDeliveryIdIsNullOrderByCreatedAtAsc(
                IdConverter.toLong(restaurantId), OrderStatus.LISTO_PARA_RETIRO.getValue(), PageRequest.of(0, limit));
        return mapWithItems(orders);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findByRestaurantAndStatusAndCreatedAtBetween(
            UUID restaurantId, Collection<OrderStatus> statuses, OffsetDateTime from, OffsetDateTime to) {
        if (restaurantId == null || statuses == null || statuses.isEmpty() || from == null || to == null) {
            return List.of();
        }
        List<String> rawStatuses = statuses.stream().map(OrderStatus::getValue).collect(Collectors.toList());
        return mapWithItems(orderRepository.findByRestaurantIdAndStatusInAndCreatedAtBetween(
                IdConverter.toLong(restaurantId), rawStatuses, from, to));
    }

    /** Mapea los pedidos cargando sus items en una sola consulta (evita N+1). */
    private List<Order> mapWithItems(List<OrderEntity> orders) {
        if (orders.isEmpty()) return List.of();

        List<Long> orderIds = orders.stream().map(OrderEntity::getId).collect(Collectors.toList());
        Map<Long, List<OrderItemEntity>> itemsByOrderId = orderItemRepository.findByOrderIdIn(orderIds).stream()
                .collect(Collectors.groupingBy(OrderItemEntity::getOrderId));

        return orders.stream()
                .map(o -> mapToOrder(o, itemsByOrderId.getOrDefault(o.getId(), List.of())))
                .collect(Collectors.toList());
    }

    private Order mapToOrder(OrderEntity entity, List<OrderItemEntity> itemEntities) {
        List<OrderItem> items = itemEntities.stream()
                .map(ie -> OrderItem.builder()
                        .id(IdConverter.toUuid(ie.getId()))
                        .productId(IdConverter.toUuid(ie.getProductId()))
                        .productName(ie.getProductName())
                        .productDescription(ie.getProductDescription())
                        .productImage(ie.getProductImage())
                        .quantity(ie.getQuantity())
                        .unitPrice(ie.getUnitPrice())
                        .lineTotal(ie.getTotal())
                        .build())
                .collect(Collectors.toList());

        return Order.builder()
                .id(IdConverter.toUuid(entity.getId()))
                .clientId(IdConverter.toUuid(entity.getClientId()))
                .restaurantId(IdConverter.toUuid(entity.getRestaurantId()))
                .deliveryId(entity.getDeliveryId() != null ? IdConverter.toUuid(entity.getDeliveryId()) : null)
                .status(OrderStatus.fromValue(entity.getStatus()))
                .address(entity.getAddress())
                .subtotal(entity.getSubtotal())
                .deliveryFee(entity.getDeliveryFee())
                .total(entity.getTotal())
                .paymentMethod(PaymentMethod.fromValue(entity.getPaymentMethod()))
                .createdAt(entity.getCreatedAt())
                .items(items)
                .build();
    }
}
