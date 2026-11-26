package com.flashdrop.catalog.application.usecase;

import org.springframework.stereotype.Service;

import com.flashdrop.catalog.application.dto.OwnerProductCommand;
import com.flashdrop.catalog.application.port.outbound.ProductRepositoryPort;
import com.flashdrop.catalog.domain.exception.OwnershipAccessDeniedException;
import com.flashdrop.catalog.domain.exception.ResourceNotFoundException;
import com.flashdrop.catalog.domain.model.Product;
import com.flashdrop.catalog.infrastructure.adapter.inbound.rest.dto.UpdateProductRequest;

@Service
public class OwnerUpdateProductUseCase {

    private final ProductRepositoryPort productRepositoryPort;
    private final UpdateProductUseCase updateProductUseCase;

    public OwnerUpdateProductUseCase(
            ProductRepositoryPort productRepositoryPort,
            UpdateProductUseCase updateProductUseCase
    ) {
        this.productRepositoryPort = productRepositoryPort;
        this.updateProductUseCase = updateProductUseCase;
    }

    public Product execute(Long restaurantId, Long productId, OwnerProductCommand command) {
        Product currentProduct = productRepositoryPort.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product not found with id: " + productId
                ));
        assertOwnership(currentProduct, restaurantId);

        return updateProductUseCase.execute(
                productId,
                new UpdateProductRequest(
                        command.categoryId(),
                        restaurantId,
                        command.name(),
                        command.description(),
                        command.price(),
                        command.image(),
                        command.available(),
                        command.dimension()
                )
        );
    }

    private void assertOwnership(Product product, Long restaurantId) {
        if (!restaurantId.equals(product.getRestaurantId())) {
            throw new OwnershipAccessDeniedException(
                    "El producto no pertenece al local seleccionado"
            );
        }
    }
}
