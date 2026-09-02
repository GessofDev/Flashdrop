package com.flashdrop.catalog.application.usecase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.flashdrop.catalog.application.port.outbound.CategoryRepositoryPort;
import com.flashdrop.catalog.application.port.outbound.ProductRepositoryPort;
import com.flashdrop.catalog.application.port.outbound.RestaurantRepositoryPort;
import com.flashdrop.catalog.domain.exception.ResourceNotFoundException;
import com.flashdrop.catalog.domain.model.Product;
import com.flashdrop.catalog.domain.valueobjects.Money;

class CreateProductUseCaseTest {

    private final ProductRepositoryPort productRepositoryPort = Mockito.mock(ProductRepositoryPort.class);
    private final CategoryRepositoryPort categoryRepositoryPort = Mockito.mock(CategoryRepositoryPort.class);
    private final RestaurantRepositoryPort restaurantRepositoryPort = Mockito.mock(RestaurantRepositoryPort.class);
    private final CreateProductUseCase useCase = new CreateProductUseCase(
            productRepositoryPort,
            categoryRepositoryPort,
            restaurantRepositoryPort
    );

    @Test
    void executeThrowsWhenCategoryDoesNotExist() {
        Product product = new Product(
                null,
                999L,
                1L,
                "Burger doble",
                "Doble carne",
                new Money(BigDecimal.valueOf(8990)),
                "assets/img/burger1.png",
                true
        );

        when(categoryRepositoryPort.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> useCase.execute(product))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Category not found with id: 999");

        verify(productRepositoryPort, never()).save(product);
    }
}
