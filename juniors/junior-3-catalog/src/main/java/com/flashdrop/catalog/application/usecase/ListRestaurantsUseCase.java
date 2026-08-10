package com.flashdrop.catalog.application.usecase;

import java.util.List;

import org.springframework.stereotype.Service;

import com.flashdrop.catalog.application.port.outbound.RestaurantRepositoryPort;
import com.flashdrop.catalog.domain.model.Restaurant;

@Service
public class ListRestaurantsUseCase {

    // Contrato que permite traer restaurantes sin saber la tecnologia de persistencia.
    private final RestaurantRepositoryPort restaurantRepositoryPort;

    public ListRestaurantsUseCase(RestaurantRepositoryPort restaurantRepositoryPort) {
        this.restaurantRepositoryPort = restaurantRepositoryPort;
    }

    public List<Restaurant> execute() {
        // Accion del sistema: listar restaurantes del catalogo.
        return restaurantRepositoryPort.findAll();
    }
}
