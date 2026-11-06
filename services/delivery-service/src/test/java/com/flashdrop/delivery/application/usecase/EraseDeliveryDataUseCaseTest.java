package com.flashdrop.delivery.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.flashdrop.delivery.application.port.outbound.DeliveryPersonRepository;
import com.flashdrop.delivery.application.port.outbound.RouteRepository;
import com.flashdrop.delivery.domain.model.DeliveryPerson;

class EraseDeliveryDataUseCaseTest {

    private final RouteRepository routes = mock(RouteRepository.class);
    private final DeliveryPersonRepository persons = mock(DeliveryPersonRepository.class);
    private final EraseDeliveryDataUseCase useCase = new EraseDeliveryDataUseCase(routes, persons);

    @Test
    void anonymizeClientRoutes_delegatesTheOrderIds() {
        when(routes.anonymizeDeliveryAddresses(List.of(1L, 2L))).thenReturn(2);

        assertThat(useCase.anonymizeClientRoutes(List.of(1L, 2L))).isEqualTo(2);
    }

    @Test
    void eraseCourier_unlinksTheRoutesAndThenDeletesTheProfile() {
        when(persons.findByUserId("77")).thenReturn(Optional.of(new DeliveryPerson(5L, "77", null, Instant.now())));

        boolean erased = useCase.eraseCourier("77");

        assertThat(erased).isTrue();
        InOrder order = inOrder(routes, persons);
        order.verify(routes).clearDeliveryPerson(5L);
        order.verify(persons).deleteByUserId("77");
    }

    @Test
    void eraseCourier_withoutProfile_isANoOp_soItCanBeRetried() {
        when(persons.findByUserId("99")).thenReturn(Optional.empty());

        assertThat(useCase.eraseCourier("99")).isFalse();
        verify(routes, never()).clearDeliveryPerson(org.mockito.ArgumentMatchers.anyLong());
        verify(persons, never()).deleteByUserId(org.mockito.ArgumentMatchers.anyString());
    }
}
