package cl.flashdrop.orders.application.usecase;

import cl.flashdrop.orders.application.OrderEnricher;
import cl.flashdrop.orders.domain.exception.ForbiddenOperationException;
import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.model.RestaurantInfo;
import cl.flashdrop.orders.domain.model.Role;
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
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * "Mis pedidos": cada usuario ve solo lo suyo según los roles de su token. Un Restaurante ve
 * los pedidos de su restaurante, un Cliente los que hizo, y nunca se listan todos los pedidos.
 */
@ExtendWith(MockitoExtension.class)
class ListOrdersUseCaseTest {

    @Mock
    private OrderRepositoryPort orderRepository;
    @Mock
    private CatalogPort catalogPort;
    @Mock
    private ClientPort clientPort;

    private ListOrdersUseCase useCase;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID RESTAURANT_ID = UUID.randomUUID();
    private static final UUID CLIENT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new ListOrdersUseCase(orderRepository, catalogPort, clientPort,
                new OrderEnricher(catalogPort, clientPort));
        org.mockito.Mockito.lenient().when(clientPort.findClientById(any())).thenReturn(Optional.empty());
    }

    private static Order order(UUID id, UUID restaurantId, UUID clientId) {
        return Order.builder().id(id).restaurantId(restaurantId).clientId(clientId).build();
    }

    @Test
    void restaurante_veLosPedidosDeSuRestaurante() {
        when(catalogPort.findOperableRestaurantIdsByUserId(USER_ID)).thenReturn(List.of(RESTAURANT_ID));
        when(orderRepository.findVisibleToStore(RESTAURANT_ID))
                .thenReturn(List.of(order(UUID.randomUUID(), RESTAURANT_ID, UUID.randomUUID())));
        when(catalogPort.findRestaurantById(RESTAURANT_ID))
                .thenReturn(Optional.of(RestaurantInfo.builder().restaurantId(RESTAURANT_ID).name("Burgers").build()));

        List<Order> result = useCase.execute(USER_ID, Set.of(Role.RESTAURANTE));

        assertEquals(1, result.size());
        assertEquals("Burgers", result.get(0).getRestaurantInfo().getName());
        verify(orderRepository, never()).findByClientId(any());
    }

    @Test
    void restauranteSinRestaurante_recibeListaVacia_yNoSeListanTodosLosPedidos() {
        when(catalogPort.findOperableRestaurantIdsByUserId(USER_ID)).thenReturn(List.of());

        List<Order> result = useCase.execute(USER_ID, Set.of(Role.RESTAURANTE));

        assertTrue(result.isEmpty());
        verify(orderRepository, never()).findVisibleToStore(any());
    }

    @Test
    void restauranteConDosLocales_agregaPedidosDeAmbosSinDuplicados() {
        UUID secondStore = UUID.randomUUID();
        Order o1 = order(UUID.randomUUID(), RESTAURANT_ID, UUID.randomUUID());
        Order o2 = order(UUID.randomUUID(), secondStore, UUID.randomUUID());
        when(catalogPort.findOperableRestaurantIdsByUserId(USER_ID)).thenReturn(List.of(RESTAURANT_ID, secondStore));
        when(orderRepository.findVisibleToStore(RESTAURANT_ID)).thenReturn(List.of(o1));
        when(orderRepository.findVisibleToStore(secondStore)).thenReturn(List.of(o2, o1));
        when(catalogPort.findRestaurantById(any())).thenReturn(Optional.empty());

        List<Order> result = useCase.execute(USER_ID, Set.of(Role.RESTAURANTE));

        assertEquals(List.of(o1.getId(), o2.getId()), result.stream().map(Order::getId).toList());
    }

    @Test
    void restauranteConLocalSolicitadoPropio_filtraSoloEseLocal() {
        Order o1 = order(UUID.randomUUID(), RESTAURANT_ID, UUID.randomUUID());
        when(catalogPort.checkRestaurantMembership(USER_ID, RESTAURANT_ID)).thenReturn(true);
        when(orderRepository.findVisibleToStore(RESTAURANT_ID)).thenReturn(List.of(o1));
        when(catalogPort.findRestaurantById(RESTAURANT_ID)).thenReturn(Optional.empty());

        List<Order> result = useCase.execute(USER_ID, Set.of(Role.RESTAURANTE), RESTAURANT_ID);

        assertEquals(List.of(o1.getId()), result.stream().map(Order::getId).toList());
        verify(catalogPort, never()).findOperableRestaurantIdsByUserId(any());
    }

    @Test
    void restauranteConLocalSolicitadoAjeno_lanzaForbidden() {
        UUID foreignStore = UUID.randomUUID();
        when(catalogPort.checkRestaurantMembership(USER_ID, foreignStore)).thenReturn(false);

        org.junit.jupiter.api.Assertions.assertThrows(ForbiddenOperationException.class,
                () -> useCase.execute(USER_ID, Set.of(Role.RESTAURANTE), foreignStore));
        verify(orderRepository, never()).findVisibleToStore(any());
    }

    @Test
    void cliente_veSoloLosPedidosQueElHizo() {
        when(clientPort.findClientIdByUserId(USER_ID)).thenReturn(Optional.of(CLIENT_ID));
        Order mine = order(UUID.randomUUID(), RESTAURANT_ID, CLIENT_ID);
        when(orderRepository.findByClientId(CLIENT_ID)).thenReturn(List.of(mine));
        when(catalogPort.findRestaurantById(RESTAURANT_ID)).thenReturn(Optional.empty());

        List<Order> result = useCase.execute(USER_ID, Set.of(Role.CLIENTE));

        assertEquals(List.of(mine.getId()), result.stream().map(Order::getId).toList());
        verify(orderRepository, never()).findVisibleToStore(any());
        verify(catalogPort, never()).findOperableRestaurantIdsByUserId(any());
    }

    @Test
    void clienteSinPerfil_recibeListaVacia() {
        when(clientPort.findClientIdByUserId(USER_ID)).thenReturn(Optional.empty());

        List<Order> result = useCase.execute(USER_ID, Set.of(Role.CLIENTE));

        assertTrue(result.isEmpty());
        verify(orderRepository, never()).findByClientId(any());
    }

    @Test
    void usuarioConAmbosRoles_veLaUnionSinDuplicados() {
        UUID shared = UUID.randomUUID();
        UUID onlyRestaurant = UUID.randomUUID();
        UUID onlyClient = UUID.randomUUID();
        when(catalogPort.findOperableRestaurantIdsByUserId(USER_ID)).thenReturn(List.of(RESTAURANT_ID));
        when(clientPort.findClientIdByUserId(USER_ID)).thenReturn(Optional.of(CLIENT_ID));
        when(orderRepository.findVisibleToStore(RESTAURANT_ID)).thenReturn(List.of(
                order(shared, RESTAURANT_ID, CLIENT_ID), order(onlyRestaurant, RESTAURANT_ID, UUID.randomUUID())));
        when(orderRepository.findByClientId(CLIENT_ID)).thenReturn(List.of(
                order(shared, RESTAURANT_ID, CLIENT_ID), order(onlyClient, UUID.randomUUID(), CLIENT_ID)));
        when(catalogPort.findRestaurantById(any())).thenReturn(Optional.empty());

        List<Order> result = useCase.execute(USER_ID, Set.of(Role.RESTAURANTE, Role.CLIENTE));

        assertEquals(List.of(shared, onlyRestaurant, onlyClient), result.stream().map(Order::getId).toList());
    }

    @Test
    void repartidorSolo_noVeNingunPedidoPorEstaVia() {
        List<Order> result = useCase.execute(USER_ID, Set.of(Role.REPARTIDOR));

        assertTrue(result.isEmpty());
        verify(orderRepository, never()).findVisibleToStore(any());
        verify(orderRepository, never()).findByClientId(any());
    }
}
