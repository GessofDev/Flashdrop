package cl.flashdrop.orders.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import cl.flashdrop.orders.application.usecase.GetAccountDeletionBlockersUseCase.Blockers;
import cl.flashdrop.orders.domain.exception.AccountDeletionBlockedException;
import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.port.ClientPort;
import cl.flashdrop.orders.domain.port.DeliveryOperationRepositoryPort;
import cl.flashdrop.orders.domain.port.DeliveryPort;
import cl.flashdrop.orders.domain.port.OrderRepositoryPort;

class EraseUserDataUseCaseTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID CLIENT = UUID.randomUUID();

    private final GetAccountDeletionBlockersUseCase blockers = mock(GetAccountDeletionBlockersUseCase.class);
    private final OrderRepositoryPort orders = mock(OrderRepositoryPort.class);
    private final ClientPort clients = mock(ClientPort.class);
    private final DeliveryPort delivery = mock(DeliveryPort.class);
    private final DeliveryOperationRepositoryPort operations = mock(DeliveryOperationRepositoryPort.class);
    private final EraseUserDataUseCase useCase =
            new EraseUserDataUseCase(blockers, orders, clients, delivery, operations);

    @Test
    void execute_withActiveOrders_isBlocked_andNothingIsTouched() {
        when(blockers.execute(USER)).thenReturn(new Blockers(1, 0, 2));

        AccountDeletionBlockedException ex =
                assertThrows(AccountDeletionBlockedException.class, () -> useCase.execute(USER));

        assertTrue(ex.getMessage().contains("como cliente: 1"));
        assertTrue(ex.getMessage().contains("como repartidor: 2"));
        verifyNoInteractions(orders, clients, delivery, operations);
    }

    @Test
    void execute_clientWithoutActiveOrders_anonymizesOrdersRoutesAndOperations_profileLast() {
        Order first = Order.builder().id(UUID.randomUUID()).build();
        Order second = Order.builder().id(UUID.randomUUID()).build();
        when(blockers.execute(USER)).thenReturn(new Blockers(0, 0, 0));
        when(clients.findClientIdByUserId(USER)).thenReturn(Optional.of(CLIENT));
        when(orders.findByClientId(CLIENT)).thenReturn(List.of(first, second));
        when(delivery.anonymizeClientRoutes(List.of(first.getId(), second.getId()))).thenReturn(2);
        when(orders.anonymizeAddressesByClient(CLIENT)).thenReturn(2);
        when(operations.anonymizeActor(USER)).thenReturn(1);
        when(clients.anonymizeByUserId(USER)).thenReturn(true);

        EraseUserDataUseCase.Result result = useCase.execute(USER);

        assertEquals(new EraseUserDataUseCase.Result(2, 2, 1, true), result);
        InOrder order = inOrder(delivery, orders, operations, clients);
        order.verify(delivery).anonymizeClientRoutes(List.of(first.getId(), second.getId()));
        order.verify(orders).anonymizeAddressesByClient(CLIENT);
        order.verify(operations).anonymizeActor(USER);
        order.verify(clients).anonymizeByUserId(USER);
    }

    @Test
    void execute_whenDeliveryFails_theProfileLinkIsKept_soTheRetryStillFindsTheOrders() {
        Order only = Order.builder().id(UUID.randomUUID()).build();
        when(blockers.execute(USER)).thenReturn(new Blockers(0, 0, 0));
        when(clients.findClientIdByUserId(USER)).thenReturn(Optional.of(CLIENT));
        when(orders.findByClientId(CLIENT)).thenReturn(List.of(only));
        when(delivery.anonymizeClientRoutes(any())).thenThrow(new IllegalStateException("Delivery no disponible"));

        assertThrows(IllegalStateException.class, () -> useCase.execute(USER));

        verify(clients, never()).anonymizeByUserId(any());
        verify(orders, never()).anonymizeAddressesByClient(any());
    }

    @Test
    void execute_userWithoutClientProfile_onlyAnonymizesOperations_andIsIdempotent() {
        when(blockers.execute(USER)).thenReturn(new Blockers(0, 0, 0));
        when(clients.findClientIdByUserId(USER)).thenReturn(Optional.empty());
        when(operations.anonymizeActor(USER)).thenReturn(0);
        when(clients.anonymizeByUserId(USER)).thenReturn(false);

        EraseUserDataUseCase.Result result = useCase.execute(USER);

        assertEquals(new EraseUserDataUseCase.Result(0, 0, 0, false), result);
        verify(delivery, never()).anonymizeClientRoutes(any());
        verify(orders, never()).anonymizeAddressesByClient(any());
    }
}
