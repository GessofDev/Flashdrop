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

class GetProductsByIdsUseCaseTest {

    private final ProductRepositoryPort productRepositoryPort = Mockito.mock(ProductRepositoryPort.class);
    private final GetProductsByIdsUseCase useCase = new GetProductsByIdsUseCase(productRepositoryPort);

    @Test
    void executeReturnsOnlyExistingProductsAndIgnoresMissingIds() {
        List<Long> requestedIds = List.of(1L, 2L, 999L);
        Product productOne = product(1L);
        Product productTwo = product(2L);
        when(productRepositoryPort.findByIds(requestedIds)).thenReturn(List.of(productOne, productTwo));

        List<Product> result = useCase.execute(requestedIds);

        assertThat(result).containsExactly(productOne, productTwo);
        verify(productRepositoryPort).findByIds(requestedIds);
    }

    private Product product(Long id) {
        return new Product(
                id,
                1L,
                1L,
                "Producto " + id,
                "Producto de prueba",
                new Money(BigDecimal.valueOf(1000)),
                "assets/img/product.png",
                true
        );
    }
}
