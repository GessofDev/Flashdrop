package com.flashdrop.catalog.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.flashdrop.catalog.application.port.outbound.MerchantMemberRepositoryPort;
import com.flashdrop.catalog.application.port.outbound.RestaurantRepositoryPort;
import com.flashdrop.catalog.domain.exception.LocalContextRequiredException;
import com.flashdrop.catalog.domain.exception.OwnershipAccessDeniedException;
import com.flashdrop.catalog.domain.exception.ResourceNotFoundException;
import com.flashdrop.catalog.domain.model.MerchantMember;
import com.flashdrop.catalog.domain.model.MerchantRole;
import com.flashdrop.catalog.domain.model.Restaurant;

class StoreContextResolverTest {

    private MerchantMemberRepositoryPort merchantMembers;
    private RestaurantRepositoryPort restaurants;
    private StoreContextResolver resolver;

    @BeforeEach
    void setUp() {
        merchantMembers = mock(MerchantMemberRepositoryPort.class);
        restaurants = mock(RestaurantRepositoryPort.class);
        resolver = new StoreContextResolver(merchantMembers, restaurants);
    }

    private static Restaurant store(Long id, Long userId, Long merchantId, String name) {
        return new Restaurant(id, userId, merchantId, name, "Address " + id,
                LocalDateTime.now(), true, List.of(), null, null, null);
    }

    @Test
    void resolveActiveStore_headerPresentAndOwned_returnsSelectedStore() {
        Restaurant s1 = store(10L, 1L, 5L, "Branch A");
        Restaurant s2 = store(11L, 1L, 5L, "Branch B");
        when(merchantMembers.findByUserId(1L))
                .thenReturn(List.of(new MerchantMember(1L, 5L, 1L, MerchantRole.DUEÑO, LocalDateTime.now())));
        when(restaurants.findByMerchantIds(List.of(5L))).thenReturn(List.of(s1, s2));
        when(restaurants.existsById(11L)).thenReturn(true);

        Restaurant resolved = resolver.resolveActiveStore(1L, "11");

        assertEquals(11L, resolved.getId());
    }

    @Test
    void resolveActiveStore_headerPresentAndForeign_throwsOwnershipAccessDenied() {
        Restaurant s1 = store(10L, 1L, 5L, "Branch A");
        when(merchantMembers.findByUserId(1L))
                .thenReturn(List.of(new MerchantMember(1L, 5L, 1L, MerchantRole.DUEÑO, LocalDateTime.now())));
        when(restaurants.findByMerchantIds(List.of(5L))).thenReturn(List.of(s1));
        when(restaurants.existsById(99L)).thenReturn(true);

        assertThrows(OwnershipAccessDeniedException.class,
                () -> resolver.resolveActiveStore(1L, "99"));
    }

    @Test
    void resolveActiveStore_headerPresentAndMissingStore_throwsResourceNotFound() {
        when(merchantMembers.findByUserId(1L)).thenReturn(List.of());
        when(restaurants.existsById(999L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class,
                () -> resolver.resolveActiveStore(1L, "999"));
    }

    @Test
    void resolveActiveStore_headerInvalidNumber_throwsIllegalArgument() {
        when(merchantMembers.findByUserId(1L)).thenReturn(List.of());

        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolveActiveStore(1L, "not-a-number"));
    }

    @Test
    void resolveActiveStore_headerAbsentAndSingleStore_returnsThatStore() {
        Restaurant s1 = store(10L, 1L, 5L, "Branch A");
        when(merchantMembers.findByUserId(1L))
                .thenReturn(List.of(new MerchantMember(1L, 5L, 1L, MerchantRole.ENCARGADO, LocalDateTime.now())));
        when(restaurants.findByMerchantIds(List.of(5L))).thenReturn(List.of(s1));

        Restaurant resolved = resolver.resolveActiveStore(1L, null);

        assertEquals(10L, resolved.getId());
    }

    @Test
    void resolveActiveStore_headerAbsentAndMultipleStores_throwsLocalContextRequired() {
        Restaurant s1 = store(10L, 1L, 5L, "Branch A");
        Restaurant s2 = store(11L, 1L, 5L, "Branch B");
        when(merchantMembers.findByUserId(1L))
                .thenReturn(List.of(new MerchantMember(1L, 5L, 1L, MerchantRole.DUEÑO, LocalDateTime.now())));
        when(restaurants.findByMerchantIds(List.of(5L))).thenReturn(List.of(s1, s2));

        LocalContextRequiredException exception = assertThrows(
                LocalContextRequiredException.class,
                () -> resolver.resolveActiveStore(1L, null));

        assertEquals(2, exception.getAvailableStores().size());
    }

    @Test
    void resolveActiveStore_headerAbsentAndNoStores_throwsResourceNotFound() {
        when(merchantMembers.findByUserId(99L)).thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class,
                () -> resolver.resolveActiveStore(99L, null));
    }

    @Test
    void resolveOperableStores_withoutMembership_returnsEmptyList() {
        when(merchantMembers.findByUserId(1L)).thenReturn(List.of());

        List<Restaurant> result = resolver.resolveOperableStores(1L);

        assertEquals(0, result.size());
    }
}
