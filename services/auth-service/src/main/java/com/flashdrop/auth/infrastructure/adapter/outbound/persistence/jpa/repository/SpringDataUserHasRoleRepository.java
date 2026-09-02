package com.flashdrop.auth.infrastructure.adapter.outbound.persistence.jpa.repository;

import com.flashdrop.auth.infrastructure.adapter.outbound.persistence.jpa.entity.UserHasRoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SpringDataUserHasRoleRepository extends JpaRepository<UserHasRoleEntity, Long> {
    List<UserHasRoleEntity> findByUserId(Long userId);
    void deleteByUserId(Long userId);
}
