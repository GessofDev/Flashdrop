package cl.flashdrop.orders.infrastructure.api;

import cl.flashdrop.orders.domain.model.DeliveryInfo;
import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.model.OrderStatus;
import cl.flashdrop.orders.domain.port.OrderRepositoryPort;
import cl.flashdrop.orders.infrastructure.adapter.outbound.IdConverter;
import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.entity.ClientEntity;
import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.repository.SpringDataClientRepository;
import cl.flashdrop.orders.infrastructure.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato interno T4: {@code GET /api/internal/orders/{id}}.
 *
 * <p>Verifica que el resumen devuelve los ids numéricos públicos, que acepta
 * id numérico o UUID y — sobre todo — que <b>no</b> expone datos de contacto
 * (dirección, teléfono, nombre del repartidor) ni detalles de la orden.</p>
 */
@ExtendWith(MockitoExtension.class)
class InternalOrderSummaryControllerTest {

    @Mock
    private OrderRepositoryPort orderRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = buildMockMvc(emptyProvider());
    }

    /**
     * El repositorio JPA de {@code client} solo existe en los perfiles con JPA.
     * En este test se simula que no está disponible: el endpoint responde con
     * {@code clientId = null} en vez de inventar un id.
     */
    private MockMvc buildMockMvc(ObjectProvider<SpringDataClientRepository> provider) {
        InternalOrderSummaryController controller = new InternalOrderSummaryController(
                orderRepository, provider);
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private ObjectProvider<SpringDataClientRepository> emptyProvider() {
        return new ObjectProvider<>() {
            @Override
            public SpringDataClientRepository getObject() {
                throw new IllegalStateException("no disponible");
            }

            @Override
            public SpringDataClientRepository getObject(Object... args) {
                throw new IllegalStateException("no disponible");
            }

            @Override
            public SpringDataClientRepository getIfAvailable() {
                return null;
            }

            @Override
            public SpringDataClientRepository getIfUnique() {
                return null;
            }
        };
    }

    private ObjectProvider<SpringDataClientRepository> providerWith(SpringDataClientRepository repo) {
        return new ObjectProvider<>() {
            @Override
            public SpringDataClientRepository getObject() {
                return repo;
            }

            @Override
            public SpringDataClientRepository getObject(Object... args) {
                return repo;
            }

            @Override
            public SpringDataClientRepository getIfAvailable() {
                return repo;
            }

            @Override
            public SpringDataClientRepository getIfUnique() {
                return repo;
            }
        };
    }

    private Order order() {
        return Order.builder()
                .id(IdConverter.toUuid(501L))
                .clientId(IdConverter.toUuid(10L))
                .restaurantId(IdConverter.toUuid(7L))
                .deliveryId(IdConverter.toUuid(9L))
                .status(OrderStatus.ENTREGADO)
                .address("Av. Providencia 1200, Santiago")
                .total(BigDecimal.valueOf(25000))
                .deliveryInfo(DeliveryInfo.builder()
                        .deliveryId(IdConverter.toUuid(9L))
                        .userId(IdConverter.toUuid(42L))
                        .name("Ana")
                        .lastName("Perez")
                        .phone("+56999999999")
                        .build())
                .build();
    }

    @Test
    void returnsTheOrderSummaryWithNumericIds() throws Exception {
        when(orderRepository.findById(IdConverter.toUuid(501L))).thenReturn(Optional.of(order()));

        mockMvc.perform(get("/api/internal/orders/{id}", 501))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(501))
                .andExpect(jsonPath("$.status").value("ENTREGADO"))
                .andExpect(jsonPath("$.restaurantId").value(7))
                .andExpect(jsonPath("$.courierUserId").value(42));
    }

    @Test
    void doesNotExposeContactDataOrOrderDetails() throws Exception {
        when(orderRepository.findById(IdConverter.toUuid(501L))).thenReturn(Optional.of(order()));

        mockMvc.perform(get("/api/internal/orders/{id}", 501))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address").doesNotExist())
                .andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.name").doesNotExist())
                .andExpect(jsonPath("$.lastName").doesNotExist())
                .andExpect(jsonPath("$.items").doesNotExist())
                .andExpect(jsonPath("$.total").doesNotExist())
                .andExpect(jsonPath("$.subtotal").doesNotExist())
                .andExpect(jsonPath("$.deliveryFee").doesNotExist());
    }

    @Test
    void acceptsTheInternalUuidAsWell() throws Exception {
        UUID internalId = IdConverter.toUuid(501L);
        when(orderRepository.findById(internalId)).thenReturn(Optional.of(order()));

        mockMvc.perform(get("/api/internal/orders/{id}", internalId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(501));
    }

    @Test
    void returnsNullCourierWhenTheOrderHasNoCourier() throws Exception {
        Order withoutCourier = Order.builder()
                .id(IdConverter.toUuid(502L))
                .clientId(IdConverter.toUuid(10L))
                .restaurantId(IdConverter.toUuid(7L))
                .status(OrderStatus.ENTREGADO)
                .address("Av. Providencia 1200, Santiago")
                .total(BigDecimal.valueOf(25000))
                .build();
        when(orderRepository.findById(IdConverter.toUuid(502L))).thenReturn(Optional.of(withoutCourier));

        mockMvc.perform(get("/api/internal/orders/{id}", 502))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courierUserId").doesNotExist());
    }

    @Test
    void returnsNotFoundWhenTheOrderDoesNotExist() throws Exception {
        when(orderRepository.findById(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/internal/orders/{id}", 999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void returnsBadRequestWhenTheIdIsNeitherNumericNorUuid() throws Exception {
        mockMvc.perform(get("/api/internal/orders/{id}", "no-es-un-id"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    void exposesTheAuthUserIdOfTheClientNotTheClientProfileId() throws Exception {
        // El perfil de cliente 10 pertenece al usuario de Auth 77: el id que
        // necesita el consumidor es el de usuario, no el del perfil.
        ClientEntity clientProfile = new ClientEntity();
        clientProfile.setId(10L);
        clientProfile.setUserId(77L);

        SpringDataClientRepository clientRepository = org.mockito.Mockito.mock(SpringDataClientRepository.class);
        when(clientRepository.findById(10L)).thenReturn(Optional.of(clientProfile));

        MockMvc mockMvcWithClientRepo = buildMockMvc(providerWith(clientRepository));
        when(orderRepository.findById(IdConverter.toUuid(501L))).thenReturn(Optional.of(order()));

        mockMvcWithClientRepo.perform(get("/api/internal/orders/{id}", 501))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value(77));
    }

    @Test
    void exposesNullClientIdWhenTheClientProfileWasAnonymized() throws Exception {
        ClientEntity anonymized = new ClientEntity();
        anonymized.setId(10L);
        anonymized.setUserId(null);

        SpringDataClientRepository clientRepository = org.mockito.Mockito.mock(SpringDataClientRepository.class);
        when(clientRepository.findById(10L)).thenReturn(Optional.of(anonymized));

        MockMvc mockMvcWithClientRepo = buildMockMvc(providerWith(clientRepository));
        when(orderRepository.findById(IdConverter.toUuid(501L))).thenReturn(Optional.of(order()));

        mockMvcWithClientRepo.perform(get("/api/internal/orders/{id}", 501))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").doesNotExist());
    }
}
