package com.flashdrop.catalog.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.flashdrop.catalog.application.port.outbound.CategoryRepositoryPort;
import com.flashdrop.catalog.domain.model.Category;

class ListCategoriesUseCaseTest {

    private final CategoryRepositoryPort categoryRepositoryPort = Mockito.mock(CategoryRepositoryPort.class);
    private final ListCategoriesUseCase useCase = new ListCategoriesUseCase(categoryRepositoryPort);

    @Test
    void executeReturnsCategoriesOrderedByName() {
        when(categoryRepositoryPort.findAll()).thenReturn(List.of(
                new Category(2L, "Pizzas", "Pizzas familiares", "assets/img/pizza.png"),
                new Category(3L, "Bebidas", "Bebidas frias", "assets/img/bag.png"),
                new Category(1L, "Hamburguesas", "Sandwiches y burgers", "assets/img/burger1.png")
        ));

        List<Category> result = useCase.execute();

        assertThat(result)
                .extracting(Category::getName)
                .containsExactly("Bebidas", "Hamburguesas", "Pizzas");
    }
}
