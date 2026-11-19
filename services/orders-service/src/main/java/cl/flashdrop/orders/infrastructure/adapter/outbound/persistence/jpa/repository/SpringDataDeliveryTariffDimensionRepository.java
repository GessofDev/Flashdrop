package cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.repository;

import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.entity.DeliveryTariffDimensionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpringDataDeliveryTariffDimensionRepository extends JpaRepository<DeliveryTariffDimensionEntity, Long> {
    List<DeliveryTariffDimensionEntity> findAllByOrderByMinDimensionAsc();
}
