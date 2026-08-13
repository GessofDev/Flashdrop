package com.flashdrop.catalog.application.port.outbound;

import java.util.List;

import com.flashdrop.catalog.domain.model.Restaurant;

// Puerto para restaurantes: permite cambiar el origen de datos sin tocar el use case.
public interface RestaurantRepositoryPort {

    List<Restaurant> findAll();
}
