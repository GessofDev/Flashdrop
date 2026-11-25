package com.flashdrop.catalog.application.usecase;

import org.springframework.stereotype.Service;

import com.flashdrop.catalog.application.port.outbound.ProductRepositoryPort;
import com.flashdrop.catalog.domain.exception.OwnershipAccessDeniedException;
import com.flashdrop.catalog.domain.exception.ResourceNotFoundException;
import com.flashdrop.catalog.domain.model.Product;

@Service
public class OwnerDeleteProductUseCase {

    private final ProductRepositoryPort productRepositoryPort;

    public OwnerDeleteProductUseCase(ProductRepositoryPort productRepositoryPort) {
        this.productRepositoryPort = productRepositoryPort;
    }

    public void execute(Long restaurantId, Long productId) {
        Product currentProduct = productRepositoryPort.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product not found with id: " + productId
                ));

        if (!restaurantId.equals(currentProduct.getRestaurantId())) {
            throw new OwnershipAccessDeniedException(
                    "El producto no pertenece al local seleccionado"
            );
        }

        productRepositoryPort.update(new Product(
                currentProduct.getId(),
                currentProduct.getCategoryId(),
                currentProduct.getRestaurantId(),
                currentProduct.getName(),
                currentProduct.getDescription(),
                currentProduct.getPrice(),
                currentProduct.getImage(),
                false
        ));
    }
}
