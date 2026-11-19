package cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa;

import cl.flashdrop.orders.domain.model.DeliveryTariff;
import cl.flashdrop.orders.domain.model.Vehicle;
import cl.flashdrop.orders.domain.port.DeliveryTariffRepositoryPort;
import cl.flashdrop.orders.infrastructure.adapter.outbound.IdConverter;
import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.entity.DeliveryTariffDimensionEntity;
import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.entity.DeliveryTariffDistanceEntity;
import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.repository.SpringDataDeliveryTariffDimensionRepository;
import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.repository.SpringDataDeliveryTariffDistanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
@Profile({"postgres", "default"})
@RequiredArgsConstructor
public class JpaDeliveryTariffRepositoryAdapter implements DeliveryTariffRepositoryPort {

    private final SpringDataDeliveryTariffDimensionRepository dimensions;
    private final SpringDataDeliveryTariffDistanceRepository distances;

    @Override
    @Transactional(readOnly = true)
    public DeliveryTariff find() {
        return new DeliveryTariff(
                dimensions.findAllByOrderByMinDimensionAsc().stream()
                        .map(e -> new DeliveryTariff.DimensionRange(
                                e.getMinDimension(), e.getMaxDimension(), Vehicle.valueOf(e.getVehicle()), e.getPrice()))
                        .toList(),
                distances.findAllByOrderByMinKmAsc().stream()
                        .map(e -> new DeliveryTariff.DistanceRange(e.getMinKm(), e.getMaxKm(), e.getPrice()))
                        .toList());
    }

    @Override
    @Transactional
    public DeliveryTariff replace(DeliveryTariff tariff, UUID updatedByUserId) {
        tariff.validate();
        OffsetDateTime now = OffsetDateTime.now();
        Long by = updatedByUserId == null ? null : IdConverter.toLong(updatedByUserId);

        // Se borra antes de insertar: los indices unicos por "desde" no admiten filas viejas y nuevas a la vez.
        dimensions.deleteAllInBatch();
        distances.deleteAllInBatch();
        dimensions.saveAll(tariff.dimensionRanges().stream()
                .map(r -> DeliveryTariffDimensionEntity.builder()
                        .minDimension(r.minDimension()).maxDimension(r.maxDimension())
                        .vehicle(r.vehicle().name()).price(r.price())
                        .updatedByUserId(by).updatedAt(now).build())
                .toList());
        distances.saveAll(tariff.distanceRanges().stream()
                .map(r -> DeliveryTariffDistanceEntity.builder()
                        .minKm(r.minKm()).maxKm(r.maxKm()).price(r.price())
                        .updatedByUserId(by).updatedAt(now).build())
                .toList());
        return find();
    }
}
