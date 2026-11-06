package cl.flashdrop.orders.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import cl.flashdrop.orders.application.usecase.GetAccountDeletionBlockersUseCase.Blockers;
import cl.flashdrop.orders.domain.port.CatalogPort;
import cl.flashdrop.orders.domain.port.ClientPort;
import cl.flashdrop.orders.domain.port.DeliveryPort;
import cl.flashdrop.orders.domain.port.OrderRepositoryPort;
import cl.flashdrop.orders.infrastructure.exception.ExternalServiceException;

class GetAccountDeletionBlockersUseCaseTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID CLIENT = UUID.randomUUID();
    private static final UUID RESTAURANT = UUID.randomUUID();
    private static final UUID COURIER = UUID.randomUUID();

    private final OrderRepositoryPort orders = mock(OrderRepositoryPort.class);
    private final ClientPort clients = mock(ClientPort.class);
    private final CatalogPort catalog = mock(CatalogPort.class);
    private final DeliveryPort delivery = mock(DeliveryPort.class);
    private final GetAccountDeletionBlockersUseCase useCase =
            new GetAccountDeletionBlockersUseCase(orders, clients, catalog, delivery);

    @Test
    void execute_countsTheActiveOrdersInEachRoleTheUserHas() {
        when(clients.findClientIdByUserId(USER)).thenReturn(Optional.of(CLIENT));
        when(catalog.findRestaurantIdByUserId(USER)).thenReturn(Optional.of(RESTAURANT));
        when(delivery.findDeliveryIdByUserId(USER)).thenReturn(Optional.of(COURIER));
        when(orders.countActiveByClient(CLIENT)).thenReturn(2);
        when(orders.countActiveByRestaurant(RESTAURANT)).thenReturn(3);
        when(orders.countActiveByDeliveryPerson(COURIER)).thenReturn(1);

        Blockers result = useCase.execute(USER);

        assertEquals(new Blockers(2, 3, 1), result);
        assertEquals(6, result.total());
    }

    @Test
    void execute_userWithoutAnyProfile_hasNoBlockers() {
        when(clients.findClientIdByUserId(USER)).thenReturn(Optional.empty());
        when(catalog.findRestaurantIdByUserId(USER)).thenReturn(Optional.empty());
        when(delivery.findDeliveryIdByUserId(USER)).thenReturn(Optional.empty());

        assertEquals(0, useCase.execute(USER).total());
    }

    @Test
    void execute_whenAnotherServiceFails_theErrorPropagates_neverReportingNoBlockers() {
        when(clients.findClientIdByUserId(USER)).thenReturn(Optional.empty());
        when(catalog.findRestaurantIdByUserId(USER))
                .thenThrow(new ExternalServiceException(HttpStatus.SERVICE_UNAVAILABLE, "Catalog no disponible"));

        assertThrows(ExternalServiceException.class, () -> useCase.execute(USER));
    }
}
