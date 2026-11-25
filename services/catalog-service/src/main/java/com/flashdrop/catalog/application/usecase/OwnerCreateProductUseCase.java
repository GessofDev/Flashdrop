package com.flashdrop.catalog.application.usecase;

import org.springframework.stereotype.Service;

import com.flashdrop.catalog.application.dto.OwnerProductCommand;
import com.flashdrop.catalog.domain.model.Product;
import com.flashdrop.catalog.domain.valueobjects.Money;

@Service
public class OwnerCreateProductUseCase {

    private final CreateProductUseCase createProductUseCase;

    public OwnerCreateProductUseCase(CreateProductUseCase createProductUseCase) {
        this.createProductUseCase = createProductUseCase;
    }

    public Product execute(Long restaurantId, OwnerProductCommand command) {
        Product product = new Product(
                null,
                command.categoryId(),
                restaurantId,
                command.name(),
                command.description(),
                new Money(command.price()),
                command.image(),
                command.available() == null || command.available(),
                command.dimension()
        );

        return createProductUseCase.execute(product);
    }
}
