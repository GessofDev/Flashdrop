package cl.flashdrop.orders.application.usecase;

import cl.flashdrop.orders.application.command.CreateOrderCommand;
import cl.flashdrop.orders.application.dto.CreatedOrderResult;
import cl.flashdrop.orders.domain.exception.OrderDomainException;
import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.port.*;
import cl.flashdrop.orders.infrastructure.adapter.outbound.http.CatalogHttpClientAdapter;
import cl.flashdrop.orders.infrastructure.adapter.outbound.http.DeliveryHttpClientAdapter;
import cl.flashdrop.orders.infrastructure.adapter.outbound.http.mock.MockCatalogServer;
import cl.flashdrop.orders.infrastructure.adapter.outbound.http.mock.MockDeliveryServer;
import cl.flashdrop.orders.infrastructure.exception.ExternalServiceException;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CreateOrderUseCaseIntegrationTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private OrderRepositoryPort orderRepository;
    private ClientPort clientPort;
    private EventPublisherPort eventPublisher;
    private CreateOrderUseCase useCase;

    private UUID userId;
    private UUID productId;
    private UUID restaurantId;
    private UUID clientId;
    private UUID orderId;

    @BeforeEach
    void setUp() {
        RestClient catalogClient = RestClient.builder()
                .baseUrl("http://localhost:" + wireMock.getPort())
                .defaultHeader("X-Internal-Api-Key", "test-internal-key")
                .defaultHeader("Accept", "application/json")
                .build();
        CatalogHttpClientAdapter catalogAdapter = new CatalogHttpClientAdapter(catalogClient);

        RestClient deliveryClient = RestClient.builder()
                .baseUrl("http://localhost:" + wireMock.getPort())
                .defaultHeader("X-Internal-Api-Key", "test-internal-key")
                .defaultHeader("Accept", "application/json")
                .defaultHeader("Content-Type", "application/json")
                .build();
        DeliveryHttpClientAdapter deliveryAdapter = new DeliveryHttpClientAdapter(deliveryClient);

        orderRepository = mock(OrderRepositoryPort.class);
        clientPort = mock(ClientPort.class);
        eventPublisher = mock(EventPublisherPort.class);

        useCase = new CreateOrderUseCase(orderRepository, catalogAdapter, clientPort, deliveryAdapter, eventPublisher);

        // Inject @Value fields via reflection (not available outside Spring context)
        setField(useCase, "deliveryFee", BigDecimal.valueOf(2500));
        setField(useCase, "defaultDistanceKm", BigDecimal.valueOf(3.2));
        setField(useCase, "defaultEstimatedMinutes", 20);
        setField(useCase, "orderCreatedRoutingKey", "order.created");

        userId = toUuid(1L);
        productId = toUuid(101L);
        restaurantId = toUuid(7L);
        clientId = toUuid(10L);
        orderId = toUuid(501L);
    }

    @Test
    void shouldCreateOrderSuccessfully() {
        MockCatalogServer.stubGetProductsByIdsOkSingle(wireMock, 101L, 7L, "Burger", "Delicious", "img.jpg", 1000, true);
        MockCatalogServer.stubGetRestaurantByIdOk(wireMock, 7L, "Burgers House", "Los Leones 300", 42L);
        MockDeliveryServer.stubCreateRouteOk(wireMock);

        when(clientPort.findClientIdByUserId(userId)).thenReturn(Optional.of(clientId));
        Order savedOrder = Order.builder()
                .id(orderId)
                .clientId(clientId)
                .restaurantId(restaurantId)
                .total(BigDecimal.valueOf(3500))
                .build();
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        CreateOrderCommand command = CreateOrderCommand.builder()
                .userId(userId)
                .address("Av. Providencia 1200")
                .paymentMethod("Tarjeta")
                .items(List.of(
                        CreateOrderCommand.ItemRequest.builder()
                                .productId(productId)
                                .quantity(1)
                                .build()
                ))
                .build();

        CreatedOrderResult result = useCase.execute(command);

        assertNotNull(result);
        assertEquals(orderId, result.id());
        assertNotNull(result.total());

        verify(orderRepository).save(any(Order.class));
        verify(eventPublisher).publish(eq("order.created"), any());
    }

    @Test
    void shouldThrowExceptionWhenProductNotFound() {
        MockCatalogServer.stubGetProductsByIdsEmpty(wireMock);

        CreateOrderCommand command = CreateOrderCommand.builder()
                .userId(userId)
                .address("Av. Providencia")
                .items(List.of(
                        CreateOrderCommand.ItemRequest.builder()
                                .productId(productId)
                                .quantity(1)
                                .build()
                ))
                .build();

        assertThrows(OrderDomainException.class, () -> useCase.execute(command));

        verify(orderRepository, never()).save(any());
    }

    @Test
    void shouldThrowExternalServiceExceptionOnCatalogServerError() {
        MockCatalogServer.stubGetProductsByIdsServerError(wireMock);

        CreateOrderCommand command = CreateOrderCommand.builder()
                .userId(userId)
                .address("Av. Providencia")
                .items(List.of(
                        CreateOrderCommand.ItemRequest.builder()
                                .productId(productId)
                                .quantity(1)
                                .build()
                ))
                .build();

        var ex = assertThrows(ExternalServiceException.class, () -> useCase.execute(command));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
        assertTrue(ex.getMessage().contains("Catalog error"));
    }

    @Test
    void shouldThrowExceptionWhenClientNotFound() {
        MockCatalogServer.stubGetProductsByIdsOkSingle(wireMock, 101L, 7L, "Burger", "Delicious", "img.jpg", 1000, true);

        when(clientPort.findClientIdByUserId(userId)).thenReturn(Optional.empty());

        CreateOrderCommand command = CreateOrderCommand.builder()
                .userId(userId)
                .address("Av. Providencia")
                .items(List.of(
                        CreateOrderCommand.ItemRequest.builder()
                                .productId(productId)
                                .quantity(1)
                                .build()
                ))
                .build();

        assertThrows(OrderDomainException.class, () -> useCase.execute(command));

        verify(orderRepository, never()).save(any());
    }

    private static UUID toUuid(long id) {
        return new UUID(0L, id);
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to set field " + fieldName, e);
        }
    }
}
