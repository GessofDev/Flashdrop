package com.flashdrop.catalog.application.usecase;

import java.util.List;

import com.flashdrop.catalog.application.port.outbound.ProductRepositoryPort;
import com.flashdrop.catalog.domain.model.Product;
import org.springframework.stereotype.Service;

@Service
public class ListProductsUseCase {

    // El use case depende del contrato, no de Supabase directamente.
    private final ProductRepositoryPort productRepositoryPort;

    public ListProductsUseCase(ProductRepositoryPort productRepositoryPort) {
        this.productRepositoryPort = productRepositoryPort;
    }

    public List<Product> execute() {
        return productRepositoryPort.findAll();
    }

    public List<Product> executeAvailable() {
        return productRepositoryPort.findAllAvailable();
    }

    public List<Product> execute(Long categoryId, Long restaurantId) {
        if (categoryId != null && restaurantId != null) {
            return productRepositoryPort.findByCategoryId(categoryId)
                    .stream()
                    .filter(product -> restaurantId.equals(product.getRestaurantId()))
                    .toList();
        }

        if (categoryId != null) {
            return productRepositoryPort.findByCategoryId(categoryId);
        }

        if (restaurantId != null) {
            return productRepositoryPort.findByRestaurantId(restaurantId);
        }

        return execute();
    }

    public List<Product> executeAvailable(Long categoryId, Long restaurantId) {
        if (categoryId != null && restaurantId != null) {
            return productRepositoryPort.findByCategoryIdAndAvailableTrue(categoryId)
                    .stream()
                    .filter(product -> restaurantId.equals(product.getRestaurantId()))
                    .toList();
        }

        if (categoryId != null) {
            return productRepositoryPort.findByCategoryIdAndAvailableTrue(categoryId);
        }

        if (restaurantId != null) {
            return productRepositoryPort.findByRestaurantIdAndAvailableTrue(restaurantId);
        }

        return executeAvailable();
    }
}
