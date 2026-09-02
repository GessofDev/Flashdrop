package cl.flashdrop.orders.application.usecase;

import cl.flashdrop.orders.domain.model.ClientInfo;
import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.model.OrderStatus;
import cl.flashdrop.orders.domain.model.RestaurantInfo;
import cl.flashdrop.orders.domain.port.CatalogPort;
import cl.flashdrop.orders.domain.port.ClientPort;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetOrderDetailUseCaseTest {

    @Mock
    private OrderRepositoryPort orderRepository;
    @Mock
    private CatalogPort catalogPort;
    @Mock
    private ClientPort clientPort;

    // OrderEnricher is real; injected manually.
    private GetOrderDetailUseCase build() {
        OrderEnricher enricher = new OrderEnricher(catalogPort, clientPort);
        return new GetOrderDetailUseCase(orderRepository, enricher);
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

        Order result = build().execute(orderId);

        assertNotNull(result.getRestaurantInfo());
        assertEquals("Burgers House", result.getRestaurantInfo().getName());
        assertNotNull(result.getClientInfo());
        assertEquals("María Pérez", result.getClientInfo().fullName());
        // Sin contrato HTTP para repartidor/ruta -> null (pendiente documentado).
        assertNull(result.getDeliveryInfo());
        assertNull(result.getRoute());
    }
}
