package com.flashdrop.catalog.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.flashdrop.catalog.application.port.outbound.MerchantMemberRepositoryPort;
import com.flashdrop.catalog.application.port.outbound.MerchantRepositoryPort;
import com.flashdrop.catalog.application.port.outbound.RestaurantRepositoryPort;
import com.flashdrop.catalog.domain.exception.ResourceNotFoundException;
import com.flashdrop.catalog.domain.model.Merchant;
import com.flashdrop.catalog.domain.model.MerchantMember;
import com.flashdrop.catalog.domain.model.MerchantRole;
import com.flashdrop.catalog.domain.model.Restaurant;

class AssignRestaurantOwnerUseCaseTest {

    private final RestaurantRepositoryPort restaurants = mock(RestaurantRepositoryPort.class);
    private final MerchantRepositoryPort merchants = mock(MerchantRepositoryPort.class);
    private final MerchantMemberRepositoryPort merchantMembers = mock(MerchantMemberRepositoryPort.class);
    private final AssignRestaurantOwnerUseCase useCase = new AssignRestaurantOwnerUseCase(
            restaurants, merchants, merchantMembers);

    @Test
    void execute_whenRestaurantHasMerchant_addsDuenoMembership() {
        Restaurant existing = new Restaurant(3L, null, 10L, "Sucursal", "Calle 1",
                LocalDateTime.now(), false, List.of(), null, null, null);
        when(restaurants.findById(3L)).thenReturn(Optional.of(existing));

        Restaurant result = useCase.execute(3L, 60L);

        assertThat(result.getMerchantId()).isEqualTo(10L);
        verify(merchantMembers).addMember(10L, 60L, MerchantRole.DUEÑO);
        verify(restaurants, never()).assignMerchant(any(), any());
    }

    @Test
    void execute_whenRestaurantHasNoMerchant_resolvesUserMerchantAndLinksRestaurant() {
        Restaurant orphan = new Restaurant(3L, null, null, "Sin Dueño", "Calle 1",
                LocalDateTime.now(), false, List.of(), null, null, null);
        Restaurant updated = new Restaurant(3L, null, 20L, "Sin Dueño", "Calle 1",
                LocalDateTime.now(), false, List.of(), null, null, null);
        when(restaurants.findById(3L)).thenReturn(Optional.of(orphan));
        when(merchantMembers.findByUserId(1L)).thenReturn(List.of(
                new MerchantMember(1L, 20L, 1L, MerchantRole.DUEÑO, LocalDateTime.now())));
        when(restaurants.assignMerchant(3L, 20L)).thenReturn(Optional.of(updated));

        Restaurant result = useCase.execute(3L, 1L);

        assertThat(result.getMerchantId()).isEqualTo(20L);
        verify(restaurants).assignMerchant(3L, 20L);
    }

    @Test
    void execute_whenRestaurantHasNoMerchantAndUserHasNoMerchant_autoCreatesMerchant() {
        Restaurant orphan = new Restaurant(3L, null, null, "Sin Dueño", "Calle 1",
                LocalDateTime.now(), false, List.of(), null, null, null);
        Restaurant updated = new Restaurant(3L, null, 30L, "Sin Dueño", "Calle 1",
                LocalDateTime.now(), false, List.of(), null, null, null);
        when(restaurants.findById(3L)).thenReturn(Optional.of(orphan));
        when(merchantMembers.findByUserId(60L)).thenReturn(List.of());
        when(merchants.create("Sin Dueño")).thenReturn(
                new Merchant(30L, "Sin Dueño", null, null, null, null, "ACTIVO", LocalDateTime.now(), LocalDateTime.now()));
        when(restaurants.assignMerchant(3L, 30L)).thenReturn(Optional.of(updated));

        Restaurant result = useCase.execute(3L, 60L);

        assertThat(result.getMerchantId()).isEqualTo(30L);
        verify(merchantMembers).addMember(30L, 60L, MerchantRole.DUEÑO);
        verify(restaurants).assignMerchant(3L, 30L);
    }

    @Test
    void execute_whenRestaurantNotFound_throwsResourceNotFound() {
        when(restaurants.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(999L, 60L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
