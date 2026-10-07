package cl.flashdrop.orders.application.usecase;

import cl.flashdrop.orders.application.OrderEnricher;
import cl.flashdrop.orders.domain.exception.OrderDomainException;
import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.model.OrderStatus;
import cl.flashdrop.orders.domain.port.OrderRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PR-orders-available (spec FR-2): pedidos que un repartidor puede tomar en un restaurante.
 * El filtro por estado / sin repartidor / FIFO lo resuelve el repositorio
 * ({@code JpaOrderRepositoryAdapterTest}); acá se cubre la validación de parámetros y el
 * enriquecimiento.
 */
@ExtendWith(MockitoExtension.class)
class ListAvailableOrdersUseCaseTest {

    @Mock
    private OrderRepositoryPort orderRepository;

    @Mock
    private OrderEnricher enricher;

    private ListAvailableOrdersUseCase useCase;

    private static final UUID RESTAURANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new ListAvailableOrdersUseCase(orderRepository, enricher);
    }

    @Test
    void shouldReturnRepositoryOrdersEnrichedInTheSameOrder() {
        Order first = Order.builder().id(UUID.randomUUID()).status(OrderStatus.LISTO_PARA_RETIRO).build();
        Order second = Order.builder().id(UUID.randomUUID()).status(OrderStatus.LISTO_PARA_RETIRO).build();
        when(orderRepository.findAvailableForDelivery(RESTAURANT_ID, 5)).thenReturn(List.of(first, second));

        List<Order> result = useCase.execute(RESTAURANT_ID, 5);

        assertEquals(List.of(first, second), result);
        verify(enricher).enrich(first);
        verify(enricher).enrich(second);
    }

    @Test
    void shouldAcceptLimitBoundaries() {
        useCase.execute(RESTAURANT_ID, 1);
        useCase.execute(RESTAURANT_ID, 50);

        verify(orderRepository).findAvailableForDelivery(RESTAURANT_ID, 1);
        verify(orderRepository).findAvailableForDelivery(RESTAURANT_ID, 50);
    }

    @Test
    void shouldRejectLimitAboveFifty() {
        OrderDomainException ex = assertThrows(OrderDomainException.class,
                () -> useCase.execute(RESTAURANT_ID, 51));

        assertEquals("El limite debe estar entre 1 y 50", ex.getMessage());
        verify(orderRepository, never()).findAvailableForDelivery(any(), anyInt());
    }

    @Test
    void shouldRejectLimitBelowOne() {
        assertThrows(OrderDomainException.class, () -> useCase.execute(RESTAURANT_ID, 0));

        verify(orderRepository, never()).findAvailableForDelivery(any(), anyInt());
    }

    @Test
    void shouldRejectMissingRestaurant() {
        OrderDomainException ex = assertThrows(OrderDomainException.class,
                () -> useCase.execute(null, 5));

        assertEquals("El restaurante es obligatorio", ex.getMessage());
        verify(orderRepository, never()).findAvailableForDelivery(any(), anyInt());
    }
}
