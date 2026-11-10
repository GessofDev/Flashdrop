package com.flashdrop.auth.infrastructure.adapter.outbound.persistence.jpa.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.flashdrop.auth.infrastructure.adapter.outbound.persistence.jpa.entity.AccountDeletionEntity;

public interface SpringDataAccountDeletionRepository extends JpaRepository<AccountDeletionEntity, Long> {

    @Query("select d from AccountDeletionEntity d where d.status = 'PENDING' and d.nextAttemptAt <= :now "
            + "order by d.nextAttemptAt asc")
    List<AccountDeletionEntity> findDue(@Param("now") Instant now, Pageable pageable);
}
