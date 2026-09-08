package com.flashdrop.auth.infrastructure.adapter.outbound.persistence.jpa.repository;

import com.flashdrop.auth.infrastructure.adapter.outbound.persistence.jpa.entity.RefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SpringDataRefreshTokenRepository extends JpaRepository<RefreshTokenEntity, Long> {
    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);
}
