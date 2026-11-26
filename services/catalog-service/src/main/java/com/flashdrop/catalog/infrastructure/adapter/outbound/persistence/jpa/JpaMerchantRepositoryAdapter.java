package com.flashdrop.catalog.infrastructure.adapter.outbound.persistence.jpa;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.flashdrop.catalog.application.port.outbound.MerchantRepositoryPort;
import com.flashdrop.catalog.domain.model.Merchant;
import com.flashdrop.catalog.infrastructure.adapter.outbound.persistence.jpa.entity.MerchantEntity;
import com.flashdrop.catalog.infrastructure.adapter.outbound.persistence.jpa.repository.SpringDataMerchantRepository;

@Repository
@Profile("postgres")
public class JpaMerchantRepositoryAdapter implements MerchantRepositoryPort {

    private final SpringDataMerchantRepository repository;

    public JpaMerchantRepositoryAdapter(SpringDataMerchantRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public Merchant create(String name) {
        return repository.save(MerchantEntity.create(name)).toDomain();
    }

    @Override
    public Optional<Merchant> findById(Long id) {
        return id == null ? Optional.empty() : repository.findById(id).map(MerchantEntity::toDomain);
    }

    @Override
    public Map<Long, Merchant> findByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return repository.findAllById(ids).stream()
                .map(MerchantEntity::toDomain)
                .collect(Collectors.toMap(Merchant::getId, Function.identity()));
    }
}
