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
import com.flashdrop.catalog.domain.model.Restaurant;
import com.flashdrop.catalog.domain.valueobjects.Money;

class OwnerProductUseCasesTest {

    private GetRestaurantByUserIdUseCase getRestaurantByUserIdUseCase;
    private ProductRepositoryPort productRepositoryPort;
    private CreateProductUseCase createProductUseCase;
    private UpdateProductUseCase updateProductUseCase;
    private Restaurant restaurant;

    @BeforeEach
    void setUp() {
        getRestaurantByUserIdUseCase = mock(GetRestaurantByUserIdUseCase.class);
        productRepositoryPort = mock(ProductRepositoryPort.class);
        createProductUseCase = mock(CreateProductUseCase.class);
        updateProductUseCase = mock(UpdateProductUseCase.class);
        restaurant = new Restaurant(10L, 7L, "Mi local", "Direccion", LocalDateTime.now());
        when(getRestaurantByUserIdUseCase.execute(7L)).thenReturn(restaurant);
    }

    @Test
    void createDerivesRestaurantFromAuthenticatedUser() {
        OwnerProductCommand command = command(true);
        Product saved = product(1L, 10L, true);
        when(createProductUseCase.execute(argThat(product -> product.getRestaurantId().equals(10L))))
                .thenReturn(saved);

        Product result = new OwnerCreateProductUseCase(
                getRestaurantByUserIdUseCase,
                createProductUseCase
        ).execute(7L, command);

        assertThat(result).isSameAs(saved);
    }

    @Test
    void listIncludesInactiveProductsForOwner() {
        Product inactive = product(1L, 10L, false);
        when(productRepositoryPort.findByRestaurantId(10L)).thenReturn(List.of(inactive));

        List<Product> result = new OwnerListProductsUseCase(
                getRestaurantByUserIdUseCase,
                productRepositoryPort
        ).execute(7L);

        assertThat(result).containsExactly(inactive);
    }

    @Test
    void updateRejectsProductFromAnotherRestaurant() {
        when(productRepositoryPort.findById(5L)).thenReturn(Optional.of(product(5L, 99L, true)));

        OwnerUpdateProductUseCase useCase = new OwnerUpdateProductUseCase(
                getRestaurantByUserIdUseCase,
                productRepositoryPort,
                updateProductUseCase
        );

        assertThatThrownBy(() -> useCase.execute(7L, 5L, command(true)))
                .isInstanceOf(OwnershipAccessDeniedException.class);
    }

    /** Plan de pruebas §2.3: la actualización no puede cambiar el restaurante del producto —
     *  el comando del dueño no trae restaurantId y siempre se usa el del usuario autenticado. */
    @Test
    void updateAlwaysUsesTheAuthenticatedUsersRestaurant() {
        Product updated = product(5L, 10L, true);
        when(productRepositoryPort.findById(5L)).thenReturn(Optional.of(product(5L, 10L, true)));
        when(updateProductUseCase.execute(eq(5L), argThat(request -> Long.valueOf(10L).equals(request.restaurantId()))))
                .thenReturn(updated);

        Product result = new OwnerUpdateProductUseCase(
                getRestaurantByUserIdUseCase,
                productRepositoryPort,
                updateProductUseCase
        ).execute(7L, 5L, command(true));

        assertThat(result).isSameAs(updated);
        verify(updateProductUseCase).execute(eq(5L), argThat(request -> Long.valueOf(10L).equals(request.restaurantId())));
    }

    @Test
    void deletePerformsSoftDelete() {
        Product product = product(5L, 10L, true);
        when(productRepositoryPort.findById(5L)).thenReturn(Optional.of(product));

        new OwnerDeleteProductUseCase(
                getRestaurantByUserIdUseCase,
                productRepositoryPort
        ).execute(7L, 5L);

        verify(productRepositoryPort).update(argThat(updated -> !updated.isAvailable()));
    }

    private OwnerProductCommand command(boolean available) {
        return new OwnerProductCommand(
                1L,
                "Producto",
                "Descripcion",
                BigDecimal.valueOf(2500),
                "products/2026/10/550e8400-e29b-41d4-a716-446655440000.webp",
                available
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
                available
        );
    }
}
