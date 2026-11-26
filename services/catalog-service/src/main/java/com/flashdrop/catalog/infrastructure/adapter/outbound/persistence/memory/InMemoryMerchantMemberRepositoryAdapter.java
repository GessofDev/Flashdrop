package com.flashdrop.catalog.infrastructure.adapter.outbound.persistence.memory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import com.flashdrop.catalog.application.port.outbound.MerchantMemberRepositoryPort;
import com.flashdrop.catalog.domain.model.MerchantMember;
import com.flashdrop.catalog.domain.model.MerchantRole;

@Repository
@Profile("local")
public class InMemoryMerchantMemberRepositoryAdapter implements MerchantMemberRepositoryPort {

    private final List<MerchantMember> members = new ArrayList<>();
    private final AtomicLong sequence = new AtomicLong(2);

    public InMemoryMerchantMemberRepositoryAdapter() {
        LocalDateTime now = LocalDateTime.now();
        members.add(new MerchantMember(1L, 1L, 1L, MerchantRole.DUEÑO, now));
        members.add(new MerchantMember(2L, 2L, 2L, MerchantRole.DUEÑO, now));
    }

    @Override
    public List<MerchantMember> findByUserId(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return members.stream()
                .filter(member -> member.getUserId().equals(userId))
                .toList();
    }

    @Override
    public Optional<MerchantMember> findByMerchantIdAndUserId(Long merchantId, Long userId) {
        if (merchantId == null || userId == null) {
            return Optional.empty();
        }
        return members.stream()
                .filter(member -> member.getMerchantId().equals(merchantId) && member.getUserId().equals(userId))
                .findFirst();
    }

    @Override
    public MerchantMember addMember(Long merchantId, Long userId, MerchantRole role) {
        Optional<MerchantMember> existing = findByMerchantIdAndUserId(merchantId, userId);
        if (existing.isPresent()) {
            return existing.get();
        }
        MerchantMember created = new MerchantMember(
                sequence.incrementAndGet(), merchantId, userId, role, LocalDateTime.now());
        members.add(created);
        return created;
    }

    @Override
    public void deleteByUserId(Long userId) {
        if (userId != null) {
            members.removeIf(member -> member.getUserId().equals(userId));
        }
    }
}
