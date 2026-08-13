package com.flashdrop.catalog.infrastructure.adapter.inbound.rest;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.flashdrop.catalog.application.usecase.ListRestaurantsUseCase;
import com.flashdrop.catalog.infrastructure.adapter.inbound.rest.dto.RestaurantResponse;

@RestController
@RequestMapping("/catalog/restaurants")
public class RestaurantController {

    // Entrada HTTP para restaurantes. El controller solo orquesta la respuesta.
    private final ListRestaurantsUseCase listRestaurantsUseCase;

    public RestaurantController(ListRestaurantsUseCase listRestaurantsUseCase) {
        this.listRestaurantsUseCase = listRestaurantsUseCase;
    }

    @GetMapping
    public List<RestaurantResponse> listRestaurants() {
        // GET /catalog/restaurants: entrega los restaurantes disponibles.
        return listRestaurantsUseCase.execute()
                .stream()
                .map(RestaurantResponse::fromDomain)
                .toList();
    }
}
