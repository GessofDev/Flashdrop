package com.flashdrop.delivery.application.port.outbound;

import java.util.Optional;

import com.flashdrop.delivery.domain.valueobjects.CourierLocation;

public interface CourierLocationStore {

    void save(Long deliveryPersonId, CourierLocation location);

    Optional<CourierLocation> findByDeliveryPersonId(Long deliveryPersonId);

    void deleteByDeliveryPersonId(Long deliveryPersonId);
}
