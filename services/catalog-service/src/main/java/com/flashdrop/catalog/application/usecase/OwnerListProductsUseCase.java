package com.flashdrop.catalog.application.usecase;

import java.util.List;

import org.springframework.stereotype.Service;

import com.flashdrop.catalog.application.port.outbound.ProductRepositoryPort;
import com.flashdrop.catalog.domain.model.Product;

@Service
public class OwnerListProductsUseCase {

    private final ProductRepositoryPort productRepositoryPort;

    public OwnerListProductsUseCase(ProductRepositoryPort productRepositoryPort) {
        this.productRepositoryPort = productRepositoryPort;
    }

    public List<Product> execute(Long restaurantId) {
        return productRepositoryPort.findByRestaurantId(restaurantId);
    }
}
