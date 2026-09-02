package cl.flashdrop.orders.infrastructure.api;

import cl.flashdrop.orders.application.usecase.ClaimDeliveryOrdersUseCase;
import cl.flashdrop.orders.infrastructure.api.dto.request.ClaimDeliveryRequest;
import cl.flashdrop.orders.infrastructure.api.dto.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DeliveryControllerTest {

    @Mock
    private ClaimDeliveryOrdersUseCase claimDeliveryOrdersUseCase;

    private DeliveryController controller;

    @BeforeEach
    void setUp() {
        controller = new DeliveryController(claimDeliveryOrdersUseCase);
    }

    @Test
    void shouldInvokeClaimDeliveryOrdersUseCase() {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        ClaimDeliveryRequest request = new ClaimDeliveryRequest();
        request.setDeliveryPersonId(userId);
        request.setOrderId(orderId);

        ApiResponse<Void> response = controller.claimOrders(request);

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals("Pedido reclamado", response.getMessage());

        verify(claimDeliveryOrdersUseCase).execute(userId, List.of(orderId));
    }
}
