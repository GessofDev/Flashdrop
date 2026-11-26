package com.flashdrop.catalog.infrastructure.adapter.outbound.persistence.memory;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import com.flashdrop.catalog.application.port.outbound.MerchantRepositoryPort;
import com.flashdrop.catalog.domain.model.Merchant;

@Repository
@Profile("local")
public class InMemoryMerchantRepositoryAdapter implements MerchantRepositoryPort {

    private final Map<Long, Merchant> merchants = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(2);

    public InMemoryMerchantRepositoryAdapter() {
        LocalDateTime now = LocalDateTime.now();
        merchants.put(1L, new Merchant(1L, "Flash Restaurant Demo", null, null, null, null, "ACTIVO", now, now));
        merchants.put(2L, new Merchant(2L, "Urban Burger Demo", null, null, null, null, "ACTIVO", now, now));
    }

    @Override
    public Merchant create(String name) {
        long id = sequence.incrementAndGet();
        LocalDateTime now = LocalDateTime.now();
        Merchant created = new Merchant(id, name, null, null, null, null, "ACTIVO", now, now);
        merchants.put(id, created);
        return created;
    }

    @Override
    public Optional<Merchant> findById(Long id) {
        return id == null ? Optional.empty() : Optional.ofNullable(merchants.get(id));
    }

    @Override
    public Map<Long, Merchant> findByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return ids.stream()
                .map(merchants::get)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toMap(Merchant::getId, Function.identity(), (a, b) -> a));
    }
}
