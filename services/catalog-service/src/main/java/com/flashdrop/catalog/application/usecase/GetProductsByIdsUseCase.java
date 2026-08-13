package com.flashdrop.catalog.application.usecase;

import java.util.List;

import org.springframework.stereotype.Service;

import com.flashdrop.catalog.application.port.outbound.ProductRepositoryPort;
import com.flashdrop.catalog.domain.model.Product;

@Service
public class GetProductsByIdsUseCase {

    // Usamos el puerto para no acoplar este caso de uso a una base de datos especifica.
    private final ProductRepositoryPort productRepositoryPort;

    public GetProductsByIdsUseCase(ProductRepositoryPort productRepositoryPort) {
        this.productRepositoryPort = productRepositoryPort;
    }

    public List<Product> execute(List<Long> ids) {
        // Si no llegan ids, no tiene sentido consultar a la base.
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        return productRepositoryPort.findByIds(ids);
    }
}
