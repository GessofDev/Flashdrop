package com.flashdrop.catalog.infrastructure.adapter.outbound.persistence.memory;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import com.flashdrop.catalog.application.port.outbound.CategoryRepositoryPort;
import com.flashdrop.catalog.domain.model.Category;

@Repository
@Profile("local")
public class InMemoryCategoryRepositoryAdapter implements CategoryRepositoryPort {

    private final List<Category> categories = List.of(
            new Category(1L, "Hamburguesas", "Sandwiches y burgers", "assets/img/burger1.png"),
            new Category(2L, "Pizzas", "Pizzas familiares e individuales", "assets/img/pizza.png"),
            new Category(3L, "Bebidas", "Bebidas frias", "assets/img/bag.png")
    );

    @Override
    public List<Category> findAll() {
        return categories;
    }
}
