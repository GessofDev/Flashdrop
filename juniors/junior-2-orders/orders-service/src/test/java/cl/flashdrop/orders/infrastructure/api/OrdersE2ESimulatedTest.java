package cl.flashdrop.orders.infrastructure.api;

import cl.flashdrop.orders.application.OrderEnricher;
import cl.flashdrop.orders.application.dto.CreatedOrderResult;
import cl.flashdrop.orders.application.usecase.*;
import cl.flashdrop.orders.domain.port.EventPublisherPort;
import cl.flashdrop.orders.infrastructure.adapter.outbound.IdConverter;
import cl.flashdrop.orders.infrastructure.adapter.outbound.http.AuthHttpClientAdapter;
import cl.flashdrop.orders.infrastructure.adapter.outbound.http.CatalogHttpClientAdapter;
import cl.flashdrop.orders.infrastructure.adapter.outbound.http.DeliveryHttpClientAdapter;
import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.supabase.SupabaseRestClientAdapter;
import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.supabase.SupabaseRestOrderRepositoryAdapter;
import cl.flashdrop.orders.infrastructure.api.dto.request.ClaimDeliveryRequest;
import cl.flashdrop.orders.infrastructure.api.dto.request.CreateOrderRequest;
import cl.flashdrop.orders.infrastructure.api.dto.response.ApiResponse;
import cl.flashdrop.orders.infrastructure.api.dto.response.OrderDetailResponse;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Validación E2E Simulada de Orders Service.
 *
 * <p>Prueba el flujo completo (CreateOrder -> Claim -> GetOrderDetail)
 * conectando los adaptadores reales de Orders (HTTP y PostgREST) hacia
 * servidores WireMock que simulan Catalog, Auth, Delivery y Supabase DB.</p>
 */
class OrdersE2ESimulatedTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private OrderController orderController;
    private DeliveryController deliveryController;

    private static final String API_KEY = "test-internal-key";
    private static final UUID USER_UUID = IdConverter.toUuid(1L);
    private static final UUID PRODUCT_UUID = IdConverter.toUuid(101L);
    private static final UUID ORDER_UUID = IdConverter.toUuid(501L);

    @BeforeEach
    void setUp() {
        wireMock.resetAll();

        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        // 1. Client REST para servicios internos (Catalog, Auth, Delivery)
        RestClient internalRestClient = RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                .baseUrl("http://localhost:" + wireMock.getPort())
                .defaultHeader("X-Internal-Api-Key", API_KEY)
                .defaultHeader("Accept", "application/json")
                .defaultHeader("Content-Type", "application/json")
                .build();

        // 2. Client REST para PostgREST (Supabase BD propia)
        RestClient supabaseRestClient = RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                .baseUrl("http://localhost:" + wireMock.getPort() + "/rest/v1")
                .defaultHeader("apikey", "test-role-key")
                .defaultHeader("Authorization", "Bearer test-role-key")
                .defaultHeader("Accept", "application/json")
                .defaultHeader("Content-Type", "application/json")
                .build();

        CatalogHttpClientAdapter catalogAdapter = new CatalogHttpClientAdapter(internalRestClient);
        AuthHttpClientAdapter authAdapter = new AuthHttpClientAdapter(internalRestClient);
        DeliveryHttpClientAdapter deliveryAdapter = new DeliveryHttpClientAdapter(internalRestClient);

        SupabaseRestClientAdapter clientAdapter = new SupabaseRestClientAdapter(supabaseRestClient, authAdapter);
        SupabaseRestOrderRepositoryAdapter orderRepositoryAdapter = new SupabaseRestOrderRepositoryAdapter(supabaseRestClient);
        EventPublisherPort eventPublisher = mock(EventPublisherPort.class);

        CreateOrderUseCase createOrderUseCase = new CreateOrderUseCase(
                orderRepositoryAdapter, catalogAdapter, clientAdapter, deliveryAdapter, eventPublisher);
        ReflectionTestUtils.setField(createOrderUseCase, "deliveryFee", BigDecimal.valueOf(2500));
        ReflectionTestUtils.setField(createOrderUseCase, "defaultDistanceKm", BigDecimal.valueOf(3.2));
        ReflectionTestUtils.setField(createOrderUseCase, "defaultEstimatedMinutes", 20);
        ReflectionTestUtils.setField(createOrderUseCase, "orderCreatedRoutingKey", "order.created");

        UpdateOrderStatusUseCase updateOrderStatusUseCase = new UpdateOrderStatusUseCase(
                orderRepositoryAdapter, deliveryAdapter, eventPublisher);
        ReflectionTestUtils.setField(updateOrderStatusUseCase, "statusUpdatedRoutingKey", "order.status.updated");

        ListOrdersUseCase listOrdersUseCase = new ListOrdersUseCase(
                orderRepositoryAdapter, catalogAdapter, new OrderEnricher(catalogAdapter, clientAdapter));

        GetOrderDetailUseCase getOrderDetailUseCase = new GetOrderDetailUseCase(
                orderRepositoryAdapter, new OrderEnricher(catalogAdapter, clientAdapter));

        ClaimDeliveryOrdersUseCase claimDeliveryOrdersUseCase = new ClaimDeliveryOrdersUseCase(
                orderRepositoryAdapter, deliveryAdapter);
        ReflectionTestUtils.setField(claimDeliveryOrdersUseCase, "maxClaimPerRoute", 3);

        orderController = new OrderController(createOrderUseCase, getOrderDetailUseCase, listOrdersUseCase, updateOrderStatusUseCase);
        deliveryController = new DeliveryController(claimDeliveryOrdersUseCase);
    }

    @Test
    void shouldExecuteSimulatedE2EFlowSuccessfully() {
        // ====================================================================
        // STUBS WIREMOCK
        // ====================================================================

        // 1. Catalog (C-1): GET /api/internal/products?ids=101
        wireMock.stubFor(get(urlPathMatching("/api/internal/products"))
                .withHeader("X-Internal-Api-Key", equalTo(API_KEY))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[{\"id\":101,\"restaurantId\":7,\"name\":\"Burger\",\"description\":\"Delicious\",\"image\":\"img.jpg\",\"price\":1000,\"available\":true}]")));

        // 2. Catalog (C-2): GET /api/internal/restaurants/7
        wireMock.stubFor(get(urlEqualTo("/api/internal/restaurants/7"))
                .withHeader("X-Internal-Api-Key", equalTo(API_KEY))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":7,\"name\":\"Burgers House\",\"address\":\"Los Leones 300\",\"userId\":42}")));

        // 3. Auth (C-4): GET /api/internal/users/1
        wireMock.stubFor(get(urlEqualTo("/api/internal/users/1"))
                .withHeader("X-Internal-Api-Key", equalTo(API_KEY))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":1,\"fullName\":\"Maria Perez\",\"email\":\"maria@test.com\",\"phone\":\"+56912345678\"}")));

        // 4. Delivery (C-6): POST /api/internal/delivery/routes
        wireMock.stubFor(post(urlEqualTo("/api/internal/delivery/routes"))
                .withHeader("X-Internal-Api-Key", equalTo(API_KEY))
                .willReturn(aResponse().withStatus(201)));

        // 5. Delivery (C-5): GET /api/internal/delivery/by-user/1
        wireMock.stubFor(get(urlEqualTo("/api/internal/delivery/by-user/1"))
                .withHeader("X-Internal-Api-Key", equalTo(API_KEY))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":9,\"fullName\":\"Carlos Delivery\",\"phone\":\"+56999999999\"}")));

        // 6. Delivery (C-7 bulk): PATCH /api/internal/delivery/routes
        wireMock.stubFor(patch(urlPathMatching("/api/internal/delivery/routes"))
                .withHeader("X-Internal-Api-Key", equalTo(API_KEY))
                .willReturn(aResponse().withStatus(204)));

        // 7. Supabase (BD propia Orders PostgREST Stubs)
        // GET /client
        wireMock.stubFor(get(urlPathMatching("/rest/v1/client"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[{\"id\":10,\"user_id\":1,\"created_at\":\"2026-08-22T00:00:00Z\"}]")));

        // POST /orders
        wireMock.stubFor(post(urlEqualTo("/rest/v1/orders"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[{\"id\":501,\"client_id\":10,\"restaurant_id\":7,\"delivery_id\":null,\"status\":\"Nuevo pedido\",\"address\":\"Av. Providencia 1200\",\"subtotal\":2000,\"delivery_fee\":2500,\"total\":4500,\"payment_method\":\"Tarjeta\",\"created_at\":\"2026-08-22T00:00:00Z\"}]")));

        // POST /order_items
        wireMock.stubFor(post(urlEqualTo("/rest/v1/order_items"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[{\"id\":1001,\"order_id\":501,\"product_id\":101,\"quantity\":2,\"unit_price\":1000,\"total\":2000}]")));

        // GET /orders?delivery_id=... (count active)
        wireMock.stubFor(get(urlPathMatching("/rest/v1/orders"))
                .withQueryParam("delivery_id", equalTo("eq.9"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));

        // GET /orders?id=in.(501) (findByIdsForClaim)
        wireMock.stubFor(get(urlPathMatching("/rest/v1/orders"))
                .withQueryParam("id", equalTo("in.(501)"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[{\"id\":501,\"client_id\":10,\"restaurant_id\":7,\"delivery_id\":null,\"status\":\"Nuevo pedido\",\"address\":\"Av. Providencia 1200\",\"subtotal\":2000,\"delivery_fee\":2500,\"total\":4500,\"payment_method\":\"Tarjeta\",\"created_at\":\"2026-08-22T00:00:00Z\"}]")));

        // PATCH /orders (claim update)
        wireMock.stubFor(patch(urlPathMatching("/rest/v1/orders"))
                .willReturn(aResponse().withStatus(204)));

        // GET /orders?id=eq.501 (getOrderDetail final)
        wireMock.stubFor(get(urlPathMatching("/rest/v1/orders"))
                .withQueryParam("id", equalTo("eq.501"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[{\"id\":501,\"client_id\":10,\"restaurant_id\":7,\"delivery_id\":9,\"status\":\"En camino\",\"address\":\"Av. Providencia 1200\",\"subtotal\":2000,\"delivery_fee\":2500,\"total\":4500,\"payment_method\":\"Tarjeta\",\"created_at\":\"2026-08-22T00:00:00Z\"}]")));

        // GET /order_items?order_id=eq.501
        wireMock.stubFor(get(urlPathMatching("/rest/v1/order_items"))
                .withQueryParam("order_id", equalTo("eq.501"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[{\"id\":1001,\"order_id\":501,\"product_id\":101,\"quantity\":2,\"unit_price\":1000,\"total\":2000}]")));


        // ====================================================================
        // PASO 1: CREAR UN PEDIDO (POST /api/orders)
        // ====================================================================
        CreateOrderRequest createReq = new CreateOrderRequest();
        createReq.setUserId(USER_UUID);
        createReq.setAddress("Av. Providencia 1200");
        createReq.setPaymentMethod("Tarjeta");
        createReq.setProductId(PRODUCT_UUID);
        createReq.setQuantity(2);

        ApiResponse<CreatedOrderResult> createResponse = orderController.createOrder(createReq);

        assertNotNull(createResponse);
        assertTrue(createResponse.isSuccess());
        assertNotNull(createResponse.getData());
        assertEquals(ORDER_UUID, createResponse.getData().id());
        assertEquals(0, BigDecimal.valueOf(4500).compareTo(createResponse.getData().total()));


        // ====================================================================
        // PASO 2: RECLAMAR EL PEDIDO (POST /api/delivery/claim)
        // ====================================================================
        ClaimDeliveryRequest claimReq = new ClaimDeliveryRequest();
        claimReq.setDeliveryPersonId(USER_UUID);
        claimReq.setOrderId(ORDER_UUID);

        ApiResponse<Void> claimResponse = deliveryController.claimOrders(claimReq);

        assertNotNull(claimResponse);
        assertTrue(claimResponse.isSuccess());
        assertEquals("Pedido reclamado", claimResponse.getMessage());


        // ====================================================================
        // PASO 3: CONSULTAR DETALLE DEL PEDIDO (GET /api/orders/{id})
        // ====================================================================
        ApiResponse<OrderDetailResponse> detailResponse = orderController.getOrderDetail(ORDER_UUID);

        assertNotNull(detailResponse);
        assertTrue(detailResponse.isSuccess());
        OrderDetailResponse detail = detailResponse.getData();
        assertNotNull(detail);
        assertEquals(ORDER_UUID, detail.getId());
        assertEquals("En camino", detail.getStatus());
        assertNotNull(detail.getClient());
        assertEquals("Maria Perez", detail.getClient().getName());
        assertEquals("maria@test.com", detail.getClient().getEmail());
        assertNotNull(detail.getRestaurant());
        assertEquals("Burgers House", detail.getRestaurant().getName());


        // ====================================================================
        // VERIFICACIONES HTTP HACIA WIREMOCK
        // ====================================================================
        wireMock.verify(getRequestedFor(urlPathMatching("/api/internal/products"))
                .withHeader("X-Internal-Api-Key", equalTo(API_KEY)));

        wireMock.verify(getRequestedFor(urlEqualTo("/api/internal/restaurants/7"))
                .withHeader("X-Internal-Api-Key", equalTo(API_KEY)));

        wireMock.verify(postRequestedFor(urlEqualTo("/api/internal/delivery/routes"))
                .withHeader("X-Internal-Api-Key", equalTo(API_KEY)));

        wireMock.verify(getRequestedFor(urlEqualTo("/api/internal/delivery/by-user/1"))
                .withHeader("X-Internal-Api-Key", equalTo(API_KEY)));

        wireMock.verify(patchRequestedFor(urlPathMatching("/api/internal/delivery/routes"))
                .withHeader("X-Internal-Api-Key", equalTo(API_KEY)));

        wireMock.verify(getRequestedFor(urlEqualTo("/api/internal/users/1"))
                .withHeader("X-Internal-Api-Key", equalTo(API_KEY)));
    }
}
