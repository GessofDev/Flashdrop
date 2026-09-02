package com.flashdrop.catalog.infrastructure.adapter.outbound.persistence.jpa;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Profile;

import com.flashdrop.catalog.application.port.outbound.ProductRepositoryPort;
import com.flashdrop.catalog.domain.model.Product;
import com.flashdrop.catalog.infrastructure.adapter.outbound.persistence.jpa.entity.ProductEntity;
import com.flashdrop.catalog.infrastructure.adapter.outbound.persistence.jpa.repository.SpringDataProductRepository;

@Repository
@Profile("postgres")
public class JpaProductRepositoryAdapter implements ProductRepositoryPort {

    private final SpringDataProductRepository repository;

    public JpaProductRepositoryAdapter(SpringDataProductRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<Product> findAll() {
        return repository.findAll()
                .stream()
                .map(productEntity -> productEntity.toDomain())
                .toList();
    }

    @Override
    public List<Product> findByIds(List<Long> ids) {
        return repository.findByIdIn(ids)
                .stream()
                .map(productEntity -> productEntity.toDomain())
                .toList();
    }

    @Override
    public List<Product> findByCategoryId(Long categoryId) {
        return repository.findByCategoryId(categoryId)
                .stream()
                .map(ProductEntity::toDomain)
                .toList();
    }

    @Override
    public List<Product> findByRestaurantId(Long restaurantId) {
        return repository.findByRestaurantId(restaurantId)
                .stream()
                .map(ProductEntity::toDomain)
                .toList();
    }

    @Override
    public Optional<Product> findById(Long id) {
        return repository.findById(id)
                .map(ProductEntity::toDomain);
    }

    @Override
    public Product save(Product product) {
        ProductEntity entity = new ProductEntity(
                product.getCategoryId(),
                product.getRestaurantId(),
                product.getName(),
                product.getDescription(),
                product.getPrice().amount(),
                product.getImage(),
                product.isAvailable()
        );

        return repository.save(entity).toDomain();
    }

    @Override
    public Product update(Product product) {
        ProductEntity entity = repository.findById(product.getId())
                .orElseThrow(() -> new IllegalArgumentException("Product does not exist with id: " + product.getId()));

        entity.updateFrom(product);
        return repository.save(entity).toDomain();
    }
}
