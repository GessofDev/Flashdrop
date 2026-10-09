package cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.repository;

import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.entity.ClientEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SpringDataClientRepository extends JpaRepository<ClientEntity, Long> {
    Optional<ClientEntity> findByUserId(Long userId);

    /** Alta idempotente: si el usuario ya tiene perfil (user_id único) no hace nada. */
    @Modifying
    @Query(value = "INSERT INTO client (user_id, created_at) VALUES (:userId, now()) ON CONFLICT (user_id) DO NOTHING",
            nativeQuery = true)
    void insertIfAbsent(@Param("userId") Long userId);
}
