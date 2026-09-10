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
        // Accion del sistema: listar productos disponibles para el catalogo.
        return productRepositoryPort.findAll();
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
}
