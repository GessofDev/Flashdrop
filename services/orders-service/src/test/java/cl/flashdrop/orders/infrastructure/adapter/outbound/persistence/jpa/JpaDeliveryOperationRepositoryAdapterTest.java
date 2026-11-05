package cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa;

import cl.flashdrop.orders.domain.model.DeliveryOperation;
import cl.flashdrop.orders.domain.model.DeliveryOperationState;
import cl.flashdrop.orders.domain.model.DeliveryOperationType;
import cl.flashdrop.orders.domain.model.OrderStatus;
import cl.flashdrop.orders.domain.model.Role;
import cl.flashdrop.orders.infrastructure.adapter.outbound.IdConverter;
import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.entity.OrderDeliveryOperationEntity;
import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.repository.SpringDataDeliveryOperationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JpaDeliveryOperationRepositoryAdapterTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-05T12:00:00Z");
    private static final UUID ORDER_ID = IdConverter.toUuid(65L);
    private static final UUID ACTOR_ID = IdConverter.toUuid(4L);
    private static final UUID DELIVERY_ID = IdConverter.toUuid(3L);

    @Mock
    private SpringDataDeliveryOperationRepository repository;

    private JpaDeliveryOperationRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new JpaDeliveryOperationRepositoryAdapter(repository);
    }

    @Test
    void creaUnaOperacionPendienteYDevuelveElIdGenerado() {
        when(repository.save(any(OrderDeliveryOperationEntity.class))).thenAnswer(invocation -> {
            OrderDeliveryOperationEntity entity = invocation.getArgument(0);
            entity.setId(77L);
            return entity;
        });

        DeliveryOperation result = adapter.create(pending());

        assertThat(result.id()).isEqualTo(77L);
        assertThat(result.state()).isEqualTo(DeliveryOperationState.PENDING);
        assertThat(result.reason()).isEqualTo("No puedo retirarme en tiempo");
        assertThat(result.actorRole()).isEqualTo(Role.REPARTIDOR);
        assertThat(result.correlationId()).isEqualTo("corr-1");
    }

    @Test
    void bloqueaUnaOperacionPorIdParaFinalizarlaDeFormaExclusiva() {
        OrderDeliveryOperationEntity entity = entity(77L, DeliveryOperationState.PENDING);
        when(repository.findByIdForUpdate(77L)).thenReturn(Optional.of(entity));

        Optional<DeliveryOperation> result = adapter.findByIdForUpdate(77L);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().id()).isEqualTo(77L);
        verify(repository).findByIdForUpdate(77L);
    }

    @Test
    void listaElHistorialDeUnPedidoEnOrdenCronologico() {
        when(repository.findByOrderIdOrderByCreatedAtAsc(65L))
                .thenReturn(List.of(entity(77L, DeliveryOperationState.SUCCEEDED)));

        List<DeliveryOperation> result = adapter.findByOrderId(ORDER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).orderId()).isEqualTo(ORDER_ID);
    }

    @Test
    void persisteElResultadoDeUnaOperacionSinCambiarSusDatosPrevios() {
        OrderDeliveryOperationEntity entity = entity(77L, DeliveryOperationState.PENDING);
        when(repository.findById(77L)).thenReturn(Optional.of(entity));
        when(repository.save(entity)).thenReturn(entity);
        DeliveryOperation succeeded = pending().withId(77L).succeed(
                null, OrderStatus.LISTO_PARA_RETIRO, NOW.plusMinutes(2));

        DeliveryOperation result = adapter.save(succeeded);

        assertThat(result.state()).isEqualTo(DeliveryOperationState.SUCCEEDED);
        assertThat(result.resultingStatus()).isEqualTo(OrderStatus.LISTO_PARA_RETIRO);
        assertThat(result.reason()).isEqualTo("No puedo retirarme en tiempo");
        verify(repository).save(entity);
    }

    @Test
    void listaOperacionesPendientesConUnLimiteExplicito() {
        when(repository.findByStateOrderByCreatedAtAsc(
                eq(DeliveryOperationState.PENDING), any(Pageable.class)))
                .thenReturn(List.of(entity(77L, DeliveryOperationState.PENDING)));

        List<DeliveryOperation> result = adapter.findByState(
                DeliveryOperationState.PENDING, 10);

        assertThat(result).hasSize(1);
        verify(repository).findByStateOrderByCreatedAtAsc(
                eq(DeliveryOperationState.PENDING), any(Pageable.class));
    }

    private static DeliveryOperation pending() {
        return DeliveryOperation.pending(
                ORDER_ID,
                DeliveryOperationType.RELEASE,
                ACTOR_ID,
                Role.REPARTIDOR,
                "No puedo retirarme en tiempo",
                null,
                DELIVERY_ID,
                OrderStatus.LISTO_PARA_RETIRO,
                "corr-1",
                NOW);
    }

    private static OrderDeliveryOperationEntity entity(Long id, DeliveryOperationState state) {
        return OrderDeliveryOperationEntity.builder()
                .id(id)
                .orderId(65L)
                .operationType(DeliveryOperationType.RELEASE.name())
                .state(state.name())
                .actorUserId(4L)
                .actorRole(Role.REPARTIDOR.name())
                .reason("No puedo retirarme en tiempo")
                .previousDeliveryId(3L)
                .previousStatus(OrderStatus.LISTO_PARA_RETIRO.getValue())
                .correlationId("corr-1")
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
    }
}
