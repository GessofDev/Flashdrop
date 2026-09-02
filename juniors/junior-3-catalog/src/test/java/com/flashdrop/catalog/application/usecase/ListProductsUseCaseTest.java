package com.flashdrop.catalog.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.flashdrop.catalog.application.port.outbound.ProductRepositoryPort;
import com.flashdrop.catalog.domain.model.Product;
import com.flashdrop.catalog.domain.valueobjects.Money;

class ListProductsUseCaseTest {

    private final ProductRepositoryPort productRepositoryPort = Mockito.mock(ProductRepositoryPort.class);
    private final ListProductsUseCase useCase = new ListProductsUseCase(productRepositoryPort);

    @Test
    void executeFiltersProductsByCategory() {
        Product product = product(1L, 10L, 20L);
        when(productRepositoryPort.findByCategoryId(10L)).thenReturn(List.of(product));

        List<Product> result = useCase.execute(10L, null);

        assertThat(result).containsExactly(product);
        verify(productRepositoryPort).findByCategoryId(10L);
    }

    @Test
    void executeFiltersProductsByRestaurant() {
        Product product = product(2L, 10L, 30L);
        when(productRepositoryPort.findByRestaurantId(30L)).thenReturn(List.of(product));

        List<Product> result = useCase.execute(null, 30L);

        assertThat(result).containsExactly(product);
        verify(productRepositoryPort).findByRestaurantId(30L);
    }

    private Product product(Long id, Long categoryId, Long restaurantId) {
        return new Product(
                id,
                categoryId,
                restaurantId,
                "Producto " + id,
                "Producto de prueba",
                new Money(BigDecimal.valueOf(1000)),
                "assets/img/product.png",
                true
        );
    }
}
