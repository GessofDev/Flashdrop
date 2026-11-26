package com.flashdrop.catalog.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.flashdrop.catalog.application.port.outbound.MerchantMemberRepositoryPort;
import com.flashdrop.catalog.application.port.outbound.MerchantRepositoryPort;
import com.flashdrop.catalog.application.port.outbound.RestaurantRepositoryPort;
import com.flashdrop.catalog.domain.model.Merchant;
import com.flashdrop.catalog.domain.model.MerchantMember;
import com.flashdrop.catalog.domain.model.MerchantRole;
import com.flashdrop.catalog.domain.model.Restaurant;

class CreateRestaurantUseCaseTest {

    private final RestaurantRepositoryPort restaurants = mock(RestaurantRepositoryPort.class);
    private final MerchantRepositoryPort merchants = mock(MerchantRepositoryPort.class);
    private final MerchantMemberRepositoryPort merchantMembers = mock(MerchantMemberRepositoryPort.class);
    private final CreateRestaurantUseCase useCase = new CreateRestaurantUseCase(
            restaurants, merchants, merchantMembers);

    private static Restaurant stored(Long owner, Long merchantId) {
        return new Restaurant(9L, owner, merchantId, "Local", "Calle 1",
                LocalDateTime.now(), false, List.of(), null, null, null);
    }

    @Test
    void execute_withNameAndOwner_autoCreatesMerchantAndClosedRestaurant() {
        when(merchantMembers.findByUserId(5L)).thenReturn(List.of());
        when(merchants.create("Local")).thenReturn(
                new Merchant(30L, "Local", null, null, null, null, "ACTIVO", LocalDateTime.now(), LocalDateTime.now()));
        when(restaurants.createInMerchant("Local", "Calle 1", 30L, false, null, null, null))
                .thenReturn(stored(null, 30L));

        Restaurant result = useCase.execute("  Local ", " Calle 1 ", 5L);

        assertThat(result.getId()).isEqualTo(9L);
        assertThat(result.getMerchantId()).isEqualTo(30L);
        verify(merchantMembers).addMember(30L, 5L, MerchantRole.DUEÑO);
        verify(restaurants).createInMerchant("Local", "Calle 1", 30L, false, null, null, null);
    }

    @Test
    void execute_withCoordinates_forwardsCoordinatesToRepository() {
        BigDecimal lat = new BigDecimal("-33.448900");
        BigDecimal lng = new BigDecimal("-70.669300");
        Restaurant saved = new Restaurant(9L, null, "Local", "Calle 1", LocalDateTime.now(), false, List.of(), lat, lng);
        when(restaurants.create("Local", "Calle 1", false, lat, lng)).thenReturn(saved);

        Restaurant result = useCase.execute("Local", "Calle 1", null, lat, lng);

        assertThat(result.getLatitude()).isEqualByComparingTo(lat);
        assertThat(result.getLongitude()).isEqualByComparingTo(lng);
        verify(restaurants).create("Local", "Calle 1", false, lat, lng);
    }

    @Test
    void execute_withoutOwner_createsWithoutCheckingOwnership() {
        when(restaurants.create("Local", null, false, null, null)).thenReturn(stored(null, null));

        useCase.execute("Local", "  ", null);

        verify(merchantMembers, never()).findByUserId(any());
    }

    @Test
    void execute_withHalfCoordinates_isRejected() {
        assertThatThrownBy(() -> useCase.execute("Local", "Calle 1", null, new BigDecimal("-33.45"), null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(restaurants, never()).create(any(), any(), anyBoolean(), any(), any());
    }

    @Test
    void execute_withBlankName_isRejected() {
        assertThatThrownBy(() -> useCase.execute("  ", "Calle", null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(restaurants, never()).create(any(), any(), anyBoolean(), any(), any());
    }

    @Test
    void execute_withNonPositiveOwner_isRejected() {
        assertThatThrownBy(() -> useCase.execute("Local", "Calle", 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void execute_whenOwnerAlreadyHasMerchant_createsSecondStoreInSameMerchant() {
        when(merchantMembers.findByUserId(5L)).thenReturn(List.of(
                new MerchantMember(1L, 30L, 5L, MerchantRole.DUEÑO, LocalDateTime.now())));
        when(restaurants.createInMerchant("Otro", "Calle", 30L, false, null, null, null))
                .thenReturn(stored(null, 30L));

        Restaurant result = useCase.execute("Otro", "Calle", 5L);

        assertThat(result.getMerchantId()).isEqualTo(30L);
        verify(merchants, never()).create(any());
        verify(restaurants).createInMerchant("Otro", "Calle", 30L, false, null, null, null);
    }
}
