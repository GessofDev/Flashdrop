package cl.flashdrop.orders.application.usecase;

import cl.flashdrop.orders.application.command.CreateOrderCommand;
import cl.flashdrop.orders.application.dto.CreatedOrderResult;
import cl.flashdrop.orders.domain.exception.OrderDomainException;
import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.model.ProductInfo;
import cl.flashdrop.orders.domain.model.RestaurantInfo;
import cl.flashdrop.orders.domain.port.CatalogPort;
import cl.flashdrop.orders.domain.port.ClientPort;
import cl.flashdrop.orders.domain.port.DeliveryPort;
import cl.flashdrop.orders.domain.port.EventPublisherPort;
import cl.flashdrop.orders.domain.port.OrderRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateOrderUseCaseTest {

    @Mock
    private OrderRepositoryPort orderRepository;
    @Mock
    private CatalogPort catalogPort;
    @Mock
    private ClientPort clientPort;
    @Mock
    private DeliveryPort deliveryPort;
    @Mock
    private EventPublisherPort eventPublisher;

    @InjectMocks
    private CreateOrderUseCase createOrderUseCase;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(createOrderUseCase, "deliveryFee", BigDecimal.valueOf(2500));
        ReflectionTestUtils.setField(createOrderUseCase, "defaultDistanceKm", BigDecimal.valueOf(3.2));
        ReflectionTestUtils.setField(createOrderUseCase, "defaultEstimatedMinutes", 20);
        ReflectionTestUtils.setField(createOrderUseCase, "orderCreatedRoutingKey", "order.created");
    }

    @Test
    void shouldCreateOrderSuccessfully() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID restaurantId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        CreateOrderCommand command = CreateOrderCommand.builder()
                .userId(userId)
                .address("Av. Providencia 1200")
                .paymentMethod("Tarjeta")
                .items(List.of(
                        CreateOrderCommand.ItemRequest.builder().productId(productId).quantity(2).build()
                ))
                .build();

        ProductInfo product = ProductInfo.builder()
                .id(productId)
                .restaurantId(restaurantId)
                .price(BigDecimal.valueOf(1000))
                .name("Burger")
                .available(true)
                .build();

        RestaurantInfo restaurant = RestaurantInfo.builder()
                .restaurantId(restaurantId)
                .name("Burgers House")
                .address("Los Leones 300")
                .build();

        when(catalogPort.findProductsByIds(List.of(productId))).thenReturn(List.of(product));
        when(clientPort.findClientIdByUserId(userId)).thenReturn(Optional.of(clientId));
        when(catalogPort.findRestaurantById(restaurantId)).thenReturn(Optional.of(restaurant));

        Order mockSavedOrder = Order.builder()
                .id(orderId)
                .total(BigDecimal.valueOf(4500))
                .build();
        when(orderRepository.save(any(Order.class))).thenReturn(mockSavedOrder);

        CreatedOrderResult result = createOrderUseCase.execute(command);

        assertNotNull(result);
        assertEquals(orderId, result.id());
        assertEquals(0, BigDecimal.valueOf(4500).compareTo(result.total()));

        verify(orderRepository).save(any(Order.class));
        verify(deliveryPort).saveRoute(any());
        verify(eventPublisher).publish(eq("order.created"), any());
    }

    @Test
    void shouldThrowExceptionWhenProductDoesNotExist() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        CreateOrderCommand command = CreateOrderCommand.builder()
                .userId(userId)
                .address("Av. Providencia")
                .items(List.of(
                        CreateOrderCommand.ItemRequest.builder().productId(productId).quantity(1).build()
                ))
                .build();

        when(catalogPort.findProductsByIds(List.of(productId))).thenReturn(List.of());

        assertThrows(OrderDomainException.class, () -> createOrderUseCase.execute(command));

        verify(orderRepository, never()).save(any());
    }

    @Test
    void shouldThrowExceptionWhenProductIsUnavailable() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID restaurantId = UUID.randomUUID();

        CreateOrderCommand command = CreateOrderCommand.builder()
                .userId(userId)
                .address("Av. Providencia")
                .items(List.of(
                        CreateOrderCommand.ItemRequest.builder().productId(productId).quantity(1).build()
                ))
                .build();

        ProductInfo productNoDisponible = ProductInfo.builder()
                .id(productId)
                .restaurantId(restaurantId)
                .price(BigDecimal.valueOf(1000))
                .name("Burger")
                .available(false)
                .build();

        when(catalogPort.findProductsByIds(List.of(productId))).thenReturn(List.of(productNoDisponible));

        assertThrows(OrderDomainException.class, () -> createOrderUseCase.execute(command));

        verify(orderRepository, never()).save(any());
        verify(deliveryPort, never()).saveRoute(any());
        verify(eventPublisher, never()).publish(any(), any());
    }

    /**
     * Requisito MIGRATION_PLAN.md §12.2 "CreateOrderTest — Lista de productos vacía lanza
     * excepción": hasta ahora sólo estaba probado a nivel de dominio puro
     * (OrderDomainTest.shouldThrowExceptionWhenProductListIsEmpty), sin verificar que el
     * caso de uso completo (con sus dependencias mockeadas) realmente dispare la regla en
     * este punto exacto del flujo.
     */
    @Test
    void shouldThrowExceptionWhenItemsListIsEmpty() {
        UUID userId = UUID.randomUUID();

        CreateOrderCommand command = CreateOrderCommand.builder()
                .userId(userId)
                .address("Av. Providencia 1200")
                .items(List.of())
                .build();

        when(catalogPort.findProductsByIds(List.of())).thenReturn(List.of());

        OrderDomainException ex = assertThrows(OrderDomainException.class,
                () -> createOrderUseCase.execute(command));

        assertEquals("El pedido debe contener al menos un producto", ex.getMessage());
        verify(orderRepository, never()).save(any());
        verify(clientPort, never()).findClientIdByUserId(any());
        verify(deliveryPort, never()).saveRoute(any());
        verify(eventPublisher, never()).publish(any(), any());
    }
}
