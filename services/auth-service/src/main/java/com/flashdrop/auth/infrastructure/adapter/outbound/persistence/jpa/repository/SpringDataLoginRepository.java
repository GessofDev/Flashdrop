package com.flashdrop.auth.infrastructure.adapter.outbound.persistence.jpa.repository;

import com.flashdrop.auth.infrastructure.adapter.outbound.persistence.jpa.entity.LoginEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SpringDataLoginRepository extends JpaRepository<LoginEntity, Long> {
    Optional<LoginEntity> findByLogin(String login);

    Optional<LoginEntity> findByUserId(Long userId);

    @Modifying
    @Query(value = "update login set status = :status, updated_at = now() where id_users = :userId",
            nativeQuery = true)
    int updateStatus(@Param("userId") Long userId, @Param("status") int status);

    @Modifying
    @Query(value = "update login set password = :hash, updated_at = now() where id_users = :userId",
            nativeQuery = true)
    int updatePassword(@Param("userId") Long userId, @Param("hash") String hash);
}
