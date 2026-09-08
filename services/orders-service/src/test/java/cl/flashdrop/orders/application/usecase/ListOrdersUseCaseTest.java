package cl.flashdrop.orders.application.usecase;

import cl.flashdrop.orders.application.OrderEnricher;
import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.model.RestaurantInfo;
import cl.flashdrop.orders.domain.port.CatalogPort;
import cl.flashdrop.orders.domain.port.ClientPort;
import cl.flashdrop.orders.domain.port.OrderRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sin test dedicado previo (auditoría 2026-09-04, sección "Tests insuficientes").
 * Cubre el filtro por restaurante de {@link ListOrdersUseCase}, incluido el caso
 * "usuario sin restaurante asociado" que la Javadoc del use case describe.
 */
@ExtendWith(MockitoExtension.class)
class ListOrdersUseCaseTest {

    @Mock
    private OrderRepositoryPort orderRepository;
    @Mock
    private CatalogPort catalogPort;

    private ListOrdersUseCase useCase;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID RESTAURANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new ListOrdersUseCase(orderRepository, catalogPort, new OrderEnricher(catalogPort, mockClientPort()));
    }

    private ClientPort mockClientPort() {
        ClientPort clientPort = org.mockito.Mockito.mock(ClientPort.class);
        // lenient: sólo se invoca cuando OrderEnricher realmente enriquece un pedido
        // (no en el caso "usuario sin restaurante", que no llega a listar nada).
        org.mockito.Mockito.lenient().when(clientPort.findClientById(any())).thenReturn(Optional.empty());
        return clientPort;
    }

    @Test
    void shouldFilterByRestaurantWhenUserOwnsARestaurant() {
        when(catalogPort.findRestaurantIdByUserId(USER_ID)).thenReturn(Optional.of(RESTAURANT_ID));
        Order order = Order.builder().id(UUID.randomUUID()).restaurantId(RESTAURANT_ID).build();
        when(orderRepository.findAll(RESTAURANT_ID)).thenReturn(List.of(order));
        when(catalogPort.findRestaurantById(RESTAURANT_ID))
                .thenReturn(Optional.of(RestaurantInfo.builder().restaurantId(RESTAURANT_ID).name("Burgers").build()));

        List<Order> result = useCase.execute(USER_ID);

        assertEquals(1, result.size());
        assertEquals(RESTAURANT_ID, result.get(0).getRestaurantId());
        verify(orderRepository).findAll(RESTAURANT_ID);
    }

    @Test
    void shouldReturnEmptyListWhenUserHasNoRestaurant() {
        when(catalogPort.findRestaurantIdByUserId(USER_ID)).thenReturn(Optional.empty());

        List<Order> result = useCase.execute(USER_ID);

        assertTrue(result.isEmpty());
        verify(orderRepository, never()).findAll(any());
    }

    @Test
    void shouldReturnAllOrdersWhenNoUserIdProvided() {
        Order order1 = Order.builder().id(UUID.randomUUID()).restaurantId(RESTAURANT_ID).build();
        Order order2 = Order.builder().id(UUID.randomUUID()).restaurantId(UUID.randomUUID()).build();
        when(orderRepository.findAll(null)).thenReturn(List.of(order1, order2));

        List<Order> result = useCase.execute(null);

        assertEquals(2, result.size());
        verify(catalogPort, never()).findRestaurantIdByUserId(any());
    }

    @Test
    void shouldEnrichEachOrderReturned() {
        Order order = Order.builder().id(UUID.randomUUID()).restaurantId(RESTAURANT_ID).build();
        when(orderRepository.findAll(null)).thenReturn(List.of(order));
        when(catalogPort.findRestaurantById(RESTAURANT_ID))
                .thenReturn(Optional.of(RestaurantInfo.builder().restaurantId(RESTAURANT_ID).name("Burgers").build()));

        List<Order> result = useCase.execute(null);

        assertEquals("Burgers", result.get(0).getRestaurantInfo().getName());
    }
}
