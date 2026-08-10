package com.flashdrop.catalog.application.usecase;

import org.springframework.stereotype.Service;

import com.flashdrop.catalog.application.port.outbound.ProductRepositoryPort;
import com.flashdrop.catalog.domain.model.Product;

@Service
public class CreateProductUseCase {

    // Spring inyecta aqui el adapter que implemente ProductRepositoryPort segun el perfil activo.
    private final ProductRepositoryPort productRepositoryPort;

    public CreateProductUseCase(ProductRepositoryPort productRepositoryPort) {
        this.productRepositoryPort = productRepositoryPort;
    }

    public Product execute(Product product) {
        // La validacion principal vive en Product/Money; aqui coordinamos el guardado.
        return productRepositoryPort.save(product);
    }
}
