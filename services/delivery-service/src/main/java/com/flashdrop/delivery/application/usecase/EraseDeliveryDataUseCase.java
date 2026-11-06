package com.flashdrop.delivery.application.usecase;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flashdrop.delivery.application.port.outbound.DeliveryPersonRepository;
import com.flashdrop.delivery.application.port.outbound.RouteRepository;

/**
 * Eliminacion de cuenta (F5): borra los datos personales que Delivery guarda. Orders ya comprobo que
 * la persona no tiene pedidos en curso, por eso aqui no se cancela nada. Todas las operaciones son
 * idempotentes para poder reintentarse.
 */
@Service
public class EraseDeliveryDataUseCase {

    private final RouteRepository routes;
    private final DeliveryPersonRepository persons;

    public EraseDeliveryDataUseCase(RouteRepository routes, DeliveryPersonRepository persons) {
        this.routes = routes;
        this.persons = persons;
    }

    /** Direcciones de entrega de un cliente que elimina su cuenta (Orders informa sus pedidos). */
    @Transactional
    public int anonymizeClientRoutes(List<Long> orderIds) {
        return routes.anonymizeDeliveryAddresses(orderIds);
    }

    /** Perfil de repartidor: se desvincula de sus rutas y se borra. */
    @Transactional
    public boolean eraseCourier(String userId) {
        return persons.findByUserId(userId)
                .map(person -> {
                    routes.clearDeliveryPerson(person.getId());
                    persons.deleteByUserId(userId);
                    return true;
                })
                .orElse(false);
    }
}
