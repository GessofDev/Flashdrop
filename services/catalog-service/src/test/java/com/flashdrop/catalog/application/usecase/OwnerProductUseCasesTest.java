package com.flashdrop.catalog.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.flashdrop.catalog.application.dto.OwnerProductCommand;
import com.flashdrop.catalog.application.port.outbound.ProductRepositoryPort;
import com.flashdrop.catalog.domain.exception.OwnershipAccessDeniedException;
import com.flashdrop.catalog.domain.model.Product;
import com.flashdrop.catalog.domain.valueobjects.Money;

class OwnerProductUseCasesTest {

    private static final Long RESTAURANT_ID = 10L;

    private ProductRepositoryPort productRepositoryPort;
    private CreateProductUseCase createProductUseCase;
    private UpdateProductUseCase updateProductUseCase;

    @BeforeEach
    void setUp() {
        productRepositoryPort = mock(ProductRepositoryPort.class);
        createProductUseCase = mock(CreateProductUseCase.class);
        updateProductUseCase = mock(UpdateProductUseCase.class);
    }

    @Test
    void createAssignsResolvedRestaurantId() {
        OwnerProductCommand command = command(true);
        Product saved = product(1L, RESTAURANT_ID, true);
        when(createProductUseCase.execute(argThat(product -> product.getRestaurantId().equals(RESTAURANT_ID))))
                .thenReturn(saved);

        Product result = new OwnerCreateProductUseCase(createProductUseCase)
                .execute(RESTAURANT_ID, command);

        assertThat(result).isSameAs(saved);
    }

    @Test
    void listIncludesInactiveProductsForResolvedRestaurant() {
        Product inactive = product(1L, RESTAURANT_ID, false);
        when(productRepositoryPort.findByRestaurantId(RESTAURANT_ID)).thenReturn(List.of(inactive));

        List<Product> result = new OwnerListProductsUseCase(productRepositoryPort)
                .execute(RESTAURANT_ID);

        assertThat(result).containsExactly(inactive);
    }

    @Test
    void updateRejectsProductFromAnotherRestaurant() {
        when(productRepositoryPort.findById(5L)).thenReturn(Optional.of(product(5L, 99L, true)));

        OwnerUpdateProductUseCase useCase = new OwnerUpdateProductUseCase(
                productRepositoryPort,
                updateProductUseCase
        );

        assertThatThrownBy(() -> useCase.execute(RESTAURANT_ID, 5L, command(true)))
                .isInstanceOf(OwnershipAccessDeniedException.class);
    }

    /** Plan de pruebas §2.3: la actualización no puede cambiar el restaurante del producto —
     *  el comando del dueño no trae restaurantId y siempre se usa el del local resuelto. */
    @Test
    void updateAlwaysUsesTheResolvedRestaurantId() {
        Product updated = product(5L, RESTAURANT_ID, true);
        when(productRepositoryPort.findById(5L)).thenReturn(Optional.of(product(5L, RESTAURANT_ID, true)));
        when(updateProductUseCase.execute(eq(5L), argThat(request -> RESTAURANT_ID.equals(request.restaurantId()))))
                .thenReturn(updated);

        Product result = new OwnerUpdateProductUseCase(
                productRepositoryPort,
                updateProductUseCase
        ).execute(RESTAURANT_ID, 5L, command(true));

        assertThat(result).isSameAs(updated);
        verify(updateProductUseCase).execute(eq(5L), argThat(request -> RESTAURANT_ID.equals(request.restaurantId())));
    }

    @Test
    void deleteRejectsProductFromAnotherRestaurant() {
        when(productRepositoryPort.findById(5L)).thenReturn(Optional.of(product(5L, 99L, true)));

        OwnerDeleteProductUseCase useCase = new OwnerDeleteProductUseCase(productRepositoryPort);

        assertThatThrownBy(() -> useCase.execute(RESTAURANT_ID, 5L))
                .isInstanceOf(OwnershipAccessDeniedException.class);
    }

    @Test
    void deletePerformsSoftDelete() {
        Product product = product(5L, RESTAURANT_ID, true);
        when(productRepositoryPort.findById(5L)).thenReturn(Optional.of(product));

        new OwnerDeleteProductUseCase(productRepositoryPort)
                .execute(RESTAURANT_ID, 5L);

        verify(productRepositoryPort).update(argThat(updated -> !updated.isAvailable()));
    }

    private OwnerProductCommand command(boolean available) {
        return new OwnerProductCommand(
                1L,
                "Producto",
                "Descripcion",
                BigDecimal.valueOf(2500),
                "products/2026/10/550e8400-e29b-41d4-a716-446655440000.webp",
                available,
                null
        );
    }

    private Product product(Long id, Long restaurantId, boolean available) {
        return new Product(
                id,
                1L,
                restaurantId,
                "Producto",
                "Descripcion",
                new Money(BigDecimal.valueOf(2500)),
                "products/2026/10/550e8400-e29b-41d4-a716-446655440000.webp",
                available,
                null
        );
    }
}
