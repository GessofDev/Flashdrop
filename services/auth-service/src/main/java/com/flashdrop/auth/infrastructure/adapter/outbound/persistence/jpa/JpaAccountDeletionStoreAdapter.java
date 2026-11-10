package com.flashdrop.auth.infrastructure.adapter.outbound.persistence.jpa;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.flashdrop.auth.application.port.outbound.AccountDeletionStore;
import com.flashdrop.auth.domain.model.AccountDeletion;
import com.flashdrop.auth.infrastructure.adapter.outbound.persistence.jpa.entity.AccountDeletionEntity;
import com.flashdrop.auth.infrastructure.adapter.outbound.persistence.jpa.repository.SpringDataAccountDeletionRepository;

@Repository
public class JpaAccountDeletionStoreAdapter implements AccountDeletionStore {

    private final SpringDataAccountDeletionRepository repository;

    public JpaAccountDeletionStoreAdapter(SpringDataAccountDeletionRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public boolean create(Long userId, Instant now) {
        if (repository.existsById(userId)) {
            return false;
        }
        repository.saveAndFlush(new AccountDeletionEntity(userId, now));
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountDeletion> findDue(Instant now, int limit) {
        return repository.findDue(now, PageRequest.of(0, limit)).stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional
    public void save(AccountDeletion deletion) {
        repository.findById(deletion.userId()).ifPresent(entity -> entity.apply(
                deletion.status().name(), deletion.attempts(), deletion.ordersDone(), deletion.deliveryDone(),
                deletion.catalogDone(), deletion.nextAttemptAt(), deletion.lastError()));
    }

    @Override
    @Transactional
    public void delete(Long userId) {
        if (repository.existsById(userId)) {
            repository.deleteById(userId);
        }
    }

    private AccountDeletion toDomain(AccountDeletionEntity e) {
        return new AccountDeletion(e.getUserId(), AccountDeletion.Status.valueOf(e.getStatus()), e.getAttempts(),
                e.isOrdersDone(), e.isDeliveryDone(), e.isCatalogDone(), e.getNextAttemptAt(), e.getLastError());
    }
}
