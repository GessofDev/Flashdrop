package cl.flashdrop.orders.application.usecase;

import cl.flashdrop.orders.domain.model.ClientInfo;
import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.model.OrderItem;
import cl.flashdrop.orders.domain.model.OrderStatus;
import cl.flashdrop.orders.domain.model.ProductInfo;
import cl.flashdrop.orders.domain.model.RestaurantInfo;
import cl.flashdrop.orders.domain.port.CatalogPort;
import cl.flashdrop.orders.domain.exception.ForbiddenOperationException;
import cl.flashdrop.orders.domain.model.Role;
import cl.flashdrop.orders.domain.port.ClientPort;
import cl.flashdrop.orders.domain.port.DeliveryPort;
import cl.flashdrop.orders.domain.port.OrderRepositoryPort;
import cl.flashdrop.orders.infrastructure.adapter.outbound.IdConverter;
import cl.flashdrop.orders.infrastructure.adapter.outbound.http.dto.InternalOrderDto;
import cl.flashdrop.orders.application.OrderEnricher;
import cl.flashdrop.orders.application.usecase.GetOrderDetailUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetOrderDetailUseCaseTest {

    private static final UUID USER = IdConverter.toUuid(42L);

    @Mock
    private OrderRepositoryPort orderRepository;
    @Mock
    private CatalogPort catalogPort;
    @Mock
    private ClientPort clientPort;
    @Mock
    private DeliveryPort deliveryPort;

    // OrderEnricher is real; injected manually.
    private GetOrderDetailUseCase build() {
        OrderEnricher enricher = new OrderEnricher(catalogPort, clientPort);
        return new GetOrderDetailUseCase(orderRepository, catalogPort, clientPort, deliveryPort, enricher);
    }

    @Test
    void shouldEnrichOrderWithClientAndRestaurant() {
        UUID orderId = IdConverter.toUuid(501L);
        UUID clientId = IdConverter.toUuid(10L);
        UUID restaurantId = IdConverter.toUuid(7L);

        Order order = Order.builder()
                .id(orderId)
                .clientId(clientId)
                .restaurantId(restaurantId)
                .status(OrderStatus.NUEVO_PEDIDO)
                .total(BigDecimal.valueOf(25000))
                .build();

        RestaurantInfo restaurant = RestaurantInfo.builder().restaurantId(restaurantId)
                .name("Burgers House").address("Los Leones 300").build();
        ClientInfo client = ClientInfo.builder().clientId(clientId)
                .fullName("María Pérez").phone("+56911111111").build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(catalogPort.findRestaurantById(restaurantId)).thenReturn(Optional.of(restaurant));
        when(clientPort.findClientById(clientId)).thenReturn(Optional.of(client));
        when(clientPort.findClientIdByUserId(USER)).thenReturn(Optional.of(clientId));

        Order result = build().execute(orderId, Set.of(Role.CLIENTE), USER);

        assertNotNull(result.getRestaurantInfo());
        assertEquals("Burgers House", result.getRestaurantInfo().getName());
        assertNotNull(result.getClientInfo());
        assertEquals("María Pérez", result.getClientInfo().fullName());
        // Sin contrato HTTP para repartidor/ruta -> null (pendiente documentado).
        assertNull(result.getDeliveryInfo());
        assertNull(result.getRoute());
    }

    @Test
    void shouldCompleteMissingProductSnapshotForLegacyOrderItems() {
        UUID orderId = IdConverter.toUuid(502L);
        UUID clientId = IdConverter.toUuid(10L);
        UUID restaurantId = IdConverter.toUuid(7L);
        UUID productId = IdConverter.toUuid(101L);
        Order order = Order.builder()
                .id(orderId)
                .clientId(clientId)
                .restaurantId(restaurantId)
                .status(OrderStatus.NUEVO_PEDIDO)
                .items(List.of(OrderItem.builder()
                        .productId(productId)
                        .quantity(1)
                        .unitPrice(BigDecimal.valueOf(8990))
                        .lineTotal(BigDecimal.valueOf(8990))
                        .build()))
                .build();
        ProductInfo product = ProductInfo.builder()
                .id(productId)
                .name("Burger doble")
                .description("Doble carne")
                .image("burger.png")
                .build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(catalogPort.findRestaurantById(restaurantId)).thenReturn(Optional.empty());
        when(clientPort.findClientById(clientId)).thenReturn(Optional.empty());
        when(catalogPort.findProductsByIds(List.of(productId))).thenReturn(List.of(product));
        when(clientPort.findClientIdByUserId(USER)).thenReturn(Optional.of(clientId));

        Order result = build().execute(orderId, Set.of(Role.CLIENTE), USER);

        assertEquals("Burger doble", result.getItems().get(0).getProductName());
        assertEquals("Doble carne", result.getItems().get(0).getProductDescription());
        assertEquals("burger.png", result.getItems().get(0).getProductImage());
    }

    // ----------------------- Quién puede ver el pedido -----------------------

    private static final UUID ORDER = IdConverter.toUuid(600L);
    private static final UUID CLIENT = IdConverter.toUuid(10L);
    private static final UUID RESTAURANT = IdConverter.toUuid(7L);
    private static final UUID DELIVERY = IdConverter.toUuid(9L);

    private void pedido(OrderStatus status, UUID deliveryId) {
        when(orderRepository.findById(ORDER)).thenReturn(Optional.of(Order.builder()
                .id(ORDER).clientId(CLIENT).restaurantId(RESTAURANT).deliveryId(deliveryId).status(status).build()));
        lenient().when(catalogPort.findRestaurantById(any())).thenReturn(Optional.empty());
        lenient().when(clientPort.findClientById(any())).thenReturn(Optional.empty());
    }

    @Test
    void clienteQueNoHizoElPedido_403() {
        pedido(OrderStatus.NUEVO_PEDIDO, null);
        when(clientPort.findClientIdByUserId(USER)).thenReturn(Optional.of(IdConverter.toUuid(99L)));

        assertThrows(ForbiddenOperationException.class,
                () -> build().execute(ORDER, Set.of(Role.CLIENTE), USER));
    }

    @Test
    void duenoDelRestauranteDelPedido_puedeVerlo() {
        pedido(OrderStatus.NUEVO_PEDIDO, null);
        when(catalogPort.findRestaurantIdByUserId(USER)).thenReturn(Optional.of(RESTAURANT));

        assertEquals(ORDER, build().execute(ORDER, Set.of(Role.RESTAURANTE), USER).getId());
    }

    @Test
    void restauranteDeOtroLocal_403() {
        pedido(OrderStatus.NUEVO_PEDIDO, null);
        when(catalogPort.findRestaurantIdByUserId(USER)).thenReturn(Optional.of(IdConverter.toUuid(99L)));

        assertThrows(ForbiddenOperationException.class,
                () -> build().execute(ORDER, Set.of(Role.RESTAURANTE), USER));
    }

    @Test
    void repartidorAsignado_puedeVerlo() {
        pedido(OrderStatus.RETIRADO, DELIVERY);
        when(deliveryPort.findDeliveryIdByUserId(USER)).thenReturn(Optional.of(DELIVERY));

        assertEquals(ORDER, build().execute(ORDER, Set.of(Role.REPARTIDOR), USER).getId());
    }

    @Test
    void repartidorNoAsignado_403() {
        pedido(OrderStatus.RETIRADO, DELIVERY);
        when(deliveryPort.findDeliveryIdByUserId(USER)).thenReturn(Optional.of(IdConverter.toUuid(99L)));

        assertThrows(ForbiddenOperationException.class,
                () -> build().execute(ORDER, Set.of(Role.REPARTIDOR), USER));
    }

    @Test
    void repartidor_puedeVerUnPedidoListoParaRetiroQueNadieHaTomado() {
        pedido(OrderStatus.LISTO_PARA_RETIRO, null);

        assertEquals(ORDER, build().execute(ORDER, Set.of(Role.REPARTIDOR), USER).getId());
    }

    @Test
    void repartidor_noPuedeVerUnPedidoSinTomarQueNoEstaListo() {
        pedido(OrderStatus.NUEVO_PEDIDO, null);

        assertThrows(ForbiddenOperationException.class,
                () -> build().execute(ORDER, Set.of(Role.REPARTIDOR), USER));
    }

    @Test
    void usuarioSinRoles_403() {
        pedido(OrderStatus.NUEVO_PEDIDO, null);

        assertThrows(ForbiddenOperationException.class,
                () -> build().execute(ORDER, Set.of(), USER));
    }

    @Test
    void pedidoInexistente_siguiendoSiendoNoEncontrado() {
        when(orderRepository.findById(ORDER)).thenReturn(Optional.empty());

        assertThrows(cl.flashdrop.orders.domain.exception.OrderDomainException.class,
                () -> build().execute(ORDER, Set.of(Role.CLIENTE), USER));
    }
}
