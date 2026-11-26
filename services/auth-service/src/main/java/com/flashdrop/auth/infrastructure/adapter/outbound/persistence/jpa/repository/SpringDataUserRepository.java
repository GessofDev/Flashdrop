package com.flashdrop.auth.infrastructure.adapter.outbound.persistence.jpa.repository;

import com.flashdrop.auth.infrastructure.adapter.outbound.persistence.jpa.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SpringDataUserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByEmail(String email);
    boolean existsByEmail(String email);
    List<UserEntity> findByIdIn(List<Long> ids);

    /**
     * Cambia solo la columna email. Reescribir la fila completa desde una
     * instantanea vieja pisaria un perfil editado en paralelo.
     */
    @Modifying
    @Query(value = "update users set email = :email, updated_at = now() where id = :id",
            nativeQuery = true)
    int updateEmail(@Param("id") Long id, @Param("email") String email);
}
