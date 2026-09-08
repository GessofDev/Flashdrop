package com.flashdrop.auth.infrastructure.adapter.outbound.persistence.jpa.repository;

import com.flashdrop.auth.infrastructure.adapter.outbound.persistence.jpa.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SpringDataUserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByEmail(String email);
    boolean existsByEmail(String email);
    List<UserEntity> findByIdIn(List<Long> ids);
}
