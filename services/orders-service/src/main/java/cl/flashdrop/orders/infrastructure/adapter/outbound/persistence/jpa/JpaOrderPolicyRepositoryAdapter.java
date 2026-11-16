package cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa;

import cl.flashdrop.orders.domain.model.OrderPolicy;
import cl.flashdrop.orders.domain.port.OrderPolicyRepositoryPort;
import cl.flashdrop.orders.infrastructure.adapter.outbound.IdConverter;
import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.entity.OrderPolicyEntity;
import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.repository.SpringDataOrderPolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
@Profile({"postgres", "default"})
@RequiredArgsConstructor
public class JpaOrderPolicyRepositoryAdapter implements OrderPolicyRepositoryPort {

    private final SpringDataOrderPolicyRepository repository;

    @Override
    @Transactional(readOnly = true)
    public Optional<OrderPolicy> findGlobal() {
        return repository.findByRestaurantIdIsNull().map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrderPolicy> findByRestaurantId(UUID restaurantId) {
        if (restaurantId == null) return Optional.empty();
        return repository.findByRestaurantId(IdConverter.toLong(restaurantId)).map(this::toDomain);
    }

    @Override
    @Transactional
    public OrderPolicy save(OrderPolicy policy, UUID updatedByUserId) {
        policy.validate();
        OffsetDateTime now = OffsetDateTime.now();
        Long rawRestaurantId = policy.getRestaurantId() == null ? null : IdConverter.toLong(policy.getRestaurantId());
        OrderPolicyEntity entity = (rawRestaurantId == null
                ? repository.findByRestaurantIdIsNull()
                : repository.findByRestaurantId(rawRestaurantId))
                .orElseGet(() -> OrderPolicyEntity.builder().restaurantId(rawRestaurantId).createdAt(now).build());
        entity.setPaymentExpirationMinutes(policy.getPaymentExpirationMinutes());
        entity.setStoreResponseMinutes(policy.getStoreResponseMinutes());
        entity.setCustomerCancelWindowMinutes(policy.getCustomerCancelWindowMinutes());
        entity.setUpdatedByUserId(updatedByUserId == null ? null : IdConverter.toLong(updatedByUserId));
        entity.setUpdatedAt(now);
        return toDomain(repository.save(entity));
    }

    private OrderPolicy toDomain(OrderPolicyEntity e) {
        return OrderPolicy.builder()
                .restaurantId(e.getRestaurantId() == null ? null : IdConverter.toUuid(e.getRestaurantId()))
                .paymentExpirationMinutes(e.getPaymentExpirationMinutes())
                .storeResponseMinutes(e.getStoreResponseMinutes())
                .customerCancelWindowMinutes(e.getCustomerCancelWindowMinutes())
                .build();
    }
}
