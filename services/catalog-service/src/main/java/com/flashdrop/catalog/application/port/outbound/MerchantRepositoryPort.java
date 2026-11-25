package com.flashdrop.catalog.application.port.outbound;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import com.flashdrop.catalog.domain.model.Merchant;

/** Persistence port for merchant brands. */
public interface MerchantRepositoryPort {

    Merchant create(String name);

    Optional<Merchant> findById(Long id);

    Map<Long, Merchant> findByIds(Collection<Long> ids);
}
