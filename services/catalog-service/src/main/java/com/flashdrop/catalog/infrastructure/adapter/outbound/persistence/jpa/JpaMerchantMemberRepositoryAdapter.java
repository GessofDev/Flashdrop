package com.flashdrop.catalog.infrastructure.adapter.outbound.persistence.jpa;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.flashdrop.catalog.application.port.outbound.MerchantMemberRepositoryPort;
import com.flashdrop.catalog.domain.model.MerchantMember;
import com.flashdrop.catalog.domain.model.MerchantRole;
import com.flashdrop.catalog.infrastructure.adapter.outbound.persistence.jpa.entity.MerchantMemberEntity;
import com.flashdrop.catalog.infrastructure.adapter.outbound.persistence.jpa.repository.SpringDataMerchantMemberRepository;

@Repository
@Profile("postgres")
public class JpaMerchantMemberRepositoryAdapter implements MerchantMemberRepositoryPort {

    private final SpringDataMerchantMemberRepository repository;

    public JpaMerchantMemberRepositoryAdapter(SpringDataMerchantMemberRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<MerchantMember> findByUserId(Long userId) {
        return userId == null ? List.of() : repository.findByUserId(userId).stream()
                .map(MerchantMemberEntity::toDomain)
                .toList();
    }

    @Override
    public Optional<MerchantMember> findByMerchantIdAndUserId(Long merchantId, Long userId) {
        if (merchantId == null || userId == null) {
            return Optional.empty();
        }
        return repository.findByMerchantIdAndUserId(merchantId, userId)
                .map(MerchantMemberEntity::toDomain);
    }

    @Override
    @Transactional
    public MerchantMember addMember(Long merchantId, Long userId, MerchantRole role) {
        return repository.findByMerchantIdAndUserId(merchantId, userId)
                .map(MerchantMemberEntity::toDomain)
                .orElseGet(() -> repository.save(MerchantMemberEntity.create(merchantId, userId, role)).toDomain());
    }

    @Override
    @Transactional
    public void deleteByUserId(Long userId) {
        if (userId != null) {
            repository.deleteByUserId(userId);
        }
    }
}
