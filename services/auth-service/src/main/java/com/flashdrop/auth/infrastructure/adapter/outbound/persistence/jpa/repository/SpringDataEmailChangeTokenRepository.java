package com.flashdrop.auth.infrastructure.adapter.outbound.persistence.jpa.repository;

import com.flashdrop.auth.infrastructure.adapter.outbound.persistence.jpa.entity.EmailChangeTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface SpringDataEmailChangeTokenRepository extends JpaRepository<EmailChangeTokenEntity, Long> {

    Optional<EmailChangeTokenEntity> findByTokenHash(String tokenHash);

    @Modifying
    @Query("""
            update EmailChangeTokenEntity t set t.usedAt = :now
            where t.tokenHash = :tokenHash and t.usedAt is null and t.expiresAt > :now
            """)
    int markUsed(@Param("tokenHash") String tokenHash, @Param("now") Instant now);

    @Modifying
    @Query("update EmailChangeTokenEntity t set t.usedAt = :now where t.userId = :userId and t.usedAt is null")
    int markAllUsed(@Param("userId") Long userId, @Param("now") Instant now);
}
