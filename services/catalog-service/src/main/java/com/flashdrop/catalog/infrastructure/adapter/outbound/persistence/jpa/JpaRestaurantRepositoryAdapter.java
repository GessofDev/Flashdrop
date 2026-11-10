package com.flashdrop.catalog.infrastructure.adapter.outbound.persistence.jpa;

import java.util.List;
import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import com.flashdrop.catalog.application.port.outbound.RestaurantRepositoryPort;
import com.flashdrop.catalog.domain.model.OpeningHours;
import com.flashdrop.catalog.domain.model.Restaurant;
import com.flashdrop.catalog.infrastructure.adapter.outbound.persistence.jpa.entity.RestaurantEntity;
import com.flashdrop.catalog.infrastructure.adapter.outbound.persistence.jpa.repository.SpringDataRestaurantRepository;

@Repository
@Profile("postgres")
public class JpaRestaurantRepositoryAdapter implements RestaurantRepositoryPort {

    private final SpringDataRestaurantRepository repository;

    public JpaRestaurantRepositoryAdapter(SpringDataRestaurantRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<Restaurant> findAll() {
        return repository.findAllByOrderByNameAsc()
                .stream()
                .map(restaurantEntity -> restaurantEntity.toDomain())
                .toList();
    }

    @Override
    public boolean existsById(Long id) {
        return id != null && repository.existsById(id);
    }

    @Override
    public Optional<Restaurant> findById(Long id) {
        return repository.findById(id)
                .map(restaurantEntity -> restaurantEntity.toDomain());
    }

    @Override
    public Optional<Restaurant> findByUserId(Long userId) {
        return repository.findByUserId(userId)
                .map(restaurantEntity -> restaurantEntity.toDomain());
    }

    @Override
    @Transactional
    public Optional<Restaurant> assignOwner(Long restaurantId, Long userId) {
        return repository.findById(restaurantId)
                .map(entity -> {
                    entity.changeOwner(userId);
                    return entity.toDomain();
                });
    }

    @Override
    @Transactional
    public Optional<Restaurant> clearOwner(Long userId) {
        return repository.findByUserId(userId)
                .map(entity -> {
                    entity.changeOwner(null);
                    entity.changeAcceptingOrders(false);
                    return entity.toDomain();
                });
    }

    @Override
    @Transactional
    public Restaurant create(String name, String address, Long ownerUserId, boolean acceptingOrders) {
        return repository.save(RestaurantEntity.create(name, address, ownerUserId, acceptingOrders)).toDomain();
    }

    @Override
    @Transactional
    public Optional<Restaurant> updateAcceptingOrders(Long restaurantId, boolean acceptingOrders) {
        return repository.findById(restaurantId)
                .map(entity -> {
                    entity.changeAcceptingOrders(acceptingOrders);
                    return entity.toDomain();
                });
    }

    @Override
    @Transactional
    public Optional<Restaurant> replaceHours(Long restaurantId, List<OpeningHours> hours) {
        return repository.findById(restaurantId)
                .map(entity -> {
                    entity.replaceHours(hours);
                    return entity.toDomain();
                });
    }
}
