package com.flashdrop.catalog.application.usecase;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import com.flashdrop.catalog.infrastructure.adapter.outbound.persistence.memory.InMemoryRestaurantRepositoryAdapter;
import org.junit.jupiter.api.Test;

class SetStoreDeliveryRadiusUseCaseTest {
    private final InMemoryRestaurantRepositoryAdapter repository = new InMemoryRestaurantRepositoryAdapter();
    private final SetStoreDeliveryRadiusUseCase useCase = new SetStoreDeliveryRadiusUseCase(
            new GetRestaurantByUserIdUseCase(repository), repository);

    @Test
    void execute_ownedStore_updatesOnlyAuthenticatedOwnerAndPreservesAcrossChanges() {
        repository.updateLocation(1L, new BigDecimal("-33.45"), new BigDecimal("-70.66"));
        assertEquals(new BigDecimal("5"), useCase.execute(1L, new BigDecimal("5")).getDeliveryRadiusKm());
        repository.updateAcceptingOrders(1L, false);
        repository.replaceHours(1L, java.util.List.of());
        repository.updateLocation(1L, new BigDecimal("-33.44"), new BigDecimal("-70.66"));
        assertEquals(new BigDecimal("5"), repository.findById(1L).orElseThrow().getDeliveryRadiusKm());
        assertNull(repository.findById(2L).orElseThrow().getDeliveryRadiusKm());
    }

    @Test
    void execute_invalidRadius_rejects() {
        for (String radius : new String[]{"0", "-1", "50.01", "5.123"}) {
            assertThrows(IllegalArgumentException.class, () -> useCase.execute(1L, new BigDecimal(radius)));
        }
        assertThrows(IllegalArgumentException.class, () -> useCase.execute(1L, null));
    }

    @Test
    void execute_missingStorePin_rejects() {
        assertThrows(IllegalArgumentException.class, () -> useCase.execute(1L, BigDecimal.TEN));
    }
}
