package com.flashdrop.catalog.infrastructure.adapter.outbound.persistence.memory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import com.flashdrop.catalog.application.port.outbound.ProductRepositoryPort;
import com.flashdrop.catalog.domain.model.Product;
import com.flashdrop.catalog.domain.valueobjects.Money;

@Repository
@Profile("local")
public class InMemoryProductRepositoryAdapter implements ProductRepositoryPort {

    private final List<Product> products = new ArrayList<>(List.of(
            new Product(
                    1L,
                    1L,
                    1L,
                    "Burger doble",
                    "Doble carne, queso y salsa de la casa",
                    new Money(BigDecimal.valueOf(8990)),
                    "assets/img/burger1.png",
                    true
            ),
            new Product(
                    2L,
                    1L,
                    1L,
                    "Papas cheddar",
                    "Papas fritas con cheddar y tocino",
                    new Money(BigDecimal.valueOf(4990)),
                    "assets/img/fries.png",
                    true
            )
    ));

    @Override
    public List<Product> findAll() {
        return products;
    }

    @Override
    public List<Product> findByIds(List<Long> ids) {
        return products.stream()
                .filter(product -> ids.contains(product.getId()))
                .toList();
    }

    @Override
    public Product save(Product product) {
        long nextId = products.stream()
                .map(Product::getId)
                .max(Long::compareTo)
                .orElse(0L) + 1L;
        Product savedProduct = new Product(
                nextId,
                product.getCategoryId(),
                product.getRestaurantId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getImage(),
                product.isAvailable()
        );
        products.add(savedProduct);
        return savedProduct;
    }
}
