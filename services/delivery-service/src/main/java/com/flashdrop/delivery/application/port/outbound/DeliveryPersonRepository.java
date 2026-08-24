package com.flashdrop.delivery.application.port.outbound;

import com.flashdrop.delivery.domain.model.DeliveryPerson;

import java.util.Optional;

public interface DeliveryPersonRepository {

    Optional<DeliveryPerson> findById(Long id);

    Optional<DeliveryPerson> findByUserId(String userId);

    DeliveryPerson save(DeliveryPerson deliveryPerson);

    boolean existsByUserId(String userId);
}