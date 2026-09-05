package cl.flashdrop.orders.infrastructure.api;

import cl.flashdrop.orders.application.usecase.ClaimDeliveryOrdersUseCase;
import cl.flashdrop.orders.infrastructure.adapter.outbound.IdConverter;
import cl.flashdrop.orders.infrastructure.api.dto.request.ClaimDeliveryRequest;
import cl.flashdrop.orders.infrastructure.api.dto.response.ApiResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

/**
 * GAP-03 (auditoría 2026-09-04): la identidad del repartidor se resuelve del JWT
 * autenticado (CurrentUserResolver), nunca del body — estos tests fijan ese contrato.
 */
@ExtendWith(MockitoExtension.class)
class DeliveryControllerTest {

    @Mock
    private ClaimDeliveryOrdersUseCase claimDeliveryOrdersUseCase;

    private DeliveryController controller;

    @BeforeEach
    void setUp() {
        controller = new DeliveryController(claimDeliveryOrdersUseCase, new CurrentUserResolver());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(String.valueOf(userId), null, List.of()));
    }

    @Test
    void shouldInvokeClaimDeliveryOrdersUseCaseUsingAuthenticatedIdentity() {
        authenticateAs(42L);
        UUID orderId = UUID.randomUUID();

        ClaimDeliveryRequest request = new ClaimDeliveryRequest();
        request.setDeliveryPersonId(UUID.randomUUID()); // ver siguiente test: se ignora
        request.setOrderId(orderId);

        ApiResponse<Void> response = controller.claimOrders(request);

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals("Pedido reclamado", response.getMessage());

        verify(claimDeliveryOrdersUseCase).execute(IdConverter.toUuid(42L), List.of(orderId));
    }

    @Test
    void shouldIgnoreDeliveryPersonIdFromBodyEvenIfDifferentFromAuthenticatedUser() {
        // GAP-03: antes del fix, este body permitía reclamar "como" un repartidor
        // arbitrario (IDOR). Ahora deliveryPersonId del body se ignora por completo.
        authenticateAs(1L);
        UUID orderId = UUID.randomUUID();
        UUID spoofedDeliveryPersonId = UUID.randomUUID();

        ClaimDeliveryRequest request = new ClaimDeliveryRequest();
        request.setDeliveryPersonId(spoofedDeliveryPersonId);
        request.setOrderId(orderId);

        controller.claimOrders(request);

        verify(claimDeliveryOrdersUseCase).execute(IdConverter.toUuid(1L), List.of(orderId));
    }

    @Test
    void shouldThrowAccessDeniedWhenNoAuthenticationPresent() {
        // Defensa adicional: si por error de configuración este endpoint quedara accesible
        // sin JWT, el controller igual rechaza la operación en vez de fallar con NPE o 500.
        ClaimDeliveryRequest request = new ClaimDeliveryRequest();
        request.setDeliveryPersonId(UUID.randomUUID());
        request.setOrderId(UUID.randomUUID());

        assertThrows(AccessDeniedException.class, () -> controller.claimOrders(request));
    }
}
