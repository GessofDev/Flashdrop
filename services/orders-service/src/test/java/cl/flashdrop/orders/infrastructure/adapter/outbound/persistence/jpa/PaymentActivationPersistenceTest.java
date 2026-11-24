package cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa;

import cl.flashdrop.orders.domain.model.OrderStatus;
import cl.flashdrop.orders.domain.port.OrderRepositoryPort;
import cl.flashdrop.orders.infrastructure.adapter.outbound.IdConverter;
import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.repository.SpringDataOrderDeliveryCodeAttemptRepository;
import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.repository.SpringDataOrderItemRepository;
import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.repository.SpringDataOrderRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaymentActivationPersistenceTest {

    @Test
    void confirmPayment_pendingOrder_onlyActivatesAsNewWithoutStoreAcceptance() {
        SpringDataOrderRepository repository = mock(SpringDataOrderRepository.class);
        JpaOrderRepositoryAdapter adapter = new JpaOrderRepositoryAdapter(repository,
                mock(SpringDataOrderItemRepository.class), mock(SpringDataOrderDeliveryCodeAttemptRepository.class));
        when(repository.updateStatusIfCurrent(78L, "Pendiente de pago", "Nuevo pedido")).thenReturn(1, 0);

        assertTrue(adapter.confirmPayment(IdConverter.toUuid(78L)));
        assertFalse(adapter.confirmPayment(IdConverter.toUuid(78L)));

        verify(repository, times(2)).updateStatusIfCurrent(78L, "Pendiente de pago", "Nuevo pedido");
        verify(repository, never()).acceptByStore(any(), any(), any(), any(), any());
        verifyNoMoreInteractions(repository);
    }

    @Test
    void confirmPayment_defaultAdapter_preservesManualStoreAcceptance() {
        OrderRepositoryPort repository = mock(OrderRepositoryPort.class, CALLS_REAL_METHODS);
        var id = IdConverter.toUuid(78L);
        when(repository.updateStatusIfCurrent(id, OrderStatus.PENDIENTE_PAGO, OrderStatus.NUEVO_PEDIDO)).thenReturn(true);

        assertTrue(repository.confirmPayment(id));

        verify(repository).updateStatusIfCurrent(id, OrderStatus.PENDIENTE_PAGO, OrderStatus.NUEVO_PEDIDO);
        verify(repository, never()).acceptByStore(any(), any(), any(), any(), any());
    }
}
