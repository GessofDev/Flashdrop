package com.flashdrop.delivery.application.usecase;

import com.flashdrop.delivery.application.dto.ClaimDeliveryRequest;
import com.flashdrop.delivery.application.dto.DeliveryPersonResponse;
import com.flashdrop.delivery.application.port.outbound.DeliveryPersonRepository;
import com.flashdrop.delivery.application.port.outbound.OrderServicePort;
import com.flashdrop.delivery.application.port.outbound.RouteRepository;
import com.flashdrop.delivery.domain.exception.DeliveryPersonNotFoundException;
import com.flashdrop.delivery.domain.exception.RouteAlreadyAssignedException;
import com.flashdrop.delivery.domain.model.DeliveryPerson;
import com.flashdrop.delivery.domain.valueobjects.VehicleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClaimDeliveryOrdersUseCaseImplTest {

    @Mock
    private DeliveryPersonRepository deliveryPersonRepository;

    @Mock
    private RouteRepository routeRepository;

    @Mock
    private OrderServicePort orderServicePort;

    private ClaimDeliveryOrdersUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new ClaimDeliveryOrdersUseCaseImpl(
                deliveryPersonRepository, routeRepository, orderServicePort);
    }

    @Nested
    @DisplayName("execute(ClaimDeliveryRequest)")
    class Execute {

        @Test
        @DisplayName("TC1: valid claim — returns DeliveryPersonResponse")
        void validClaim_returnsDeliveryPersonResponse() {
            Long deliveryPersonId = 5L;
            Long orderId = 101L;
            ClaimDeliveryRequest request = new ClaimDeliveryRequest(
                    deliveryPersonId, List.of(orderId));

            DeliveryPerson person = new DeliveryPerson(
                    deliveryPersonId, 1L, VehicleType.MOTO, Instant.now());
            OrderServicePort.OrderInfo orderInfo = new OrderServicePort.OrderInfo(
                    orderId, 10L, "Pickup St 1", "Delivery St 1");

            when(deliveryPersonRepository.findById(deliveryPersonId))
                    .thenReturn(java.util.Optional.of(person));
            when(orderServicePort.getOrdersByIds(List.of(orderId)))
                    .thenReturn(List.of(orderInfo));
            when(orderServicePort.areOrdersFromSameRestaurant(List.of(orderId)))
                    .thenReturn(true);
            when(routeRepository.existsByOrderId(orderId)).thenReturn(false);
            when(routeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            List<DeliveryPersonResponse> result = useCase.execute(request);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).id()).isEqualTo(deliveryPersonId);
        }

        @Test
        @DisplayName("TC2: missing delivery person — throws DeliveryPersonNotFoundException")
        void missingDeliveryPerson_throwsException() {
            when(deliveryPersonRepository.findById(999L))
                    .thenReturn(java.util.Optional.empty());

            ClaimDeliveryRequest request = new ClaimDeliveryRequest(999L, List.of(101L));

            assertThatThrownBy(() -> useCase.execute(request))
                    .isInstanceOf(DeliveryPersonNotFoundException.class)
                    .hasMessageContaining("999");
        }

        @Test
        @DisplayName("TC3: order already assigned — throws RouteAlreadyAssignedException")
        void orderAlreadyAssigned_throwsRouteAlreadyAssignedException() {
            Long orderId = 101L;
            ClaimDeliveryRequest request = new ClaimDeliveryRequest(5L, List.of(orderId));

            DeliveryPerson person = new DeliveryPerson(5L, 1L, VehicleType.MOTO, Instant.now());
            OrderServicePort.OrderInfo orderInfo = new OrderServicePort.OrderInfo(
                    orderId, 10L, "Pickup", "Delivery");

            when(deliveryPersonRepository.findById(5L))
                    .thenReturn(java.util.Optional.of(person));
            when(orderServicePort.getOrdersByIds(List.of(orderId)))
                    .thenReturn(List.of(orderInfo));
            when(orderServicePort.areOrdersFromSameRestaurant(List.of(orderId)))
                    .thenReturn(true);
            when(routeRepository.existsByOrderId(orderId)).thenReturn(true);

            assertThatThrownBy(() -> useCase.execute(request))
                    .isInstanceOf(RouteAlreadyAssignedException.class)
                    .hasMessageContaining(String.valueOf(orderId));
        }

        @Test
        @DisplayName("TC4: orders from different restaurants — throws IllegalArgumentException")
        void multiRestaurant_throwsIllegalArgumentException() {
            ClaimDeliveryRequest request = new ClaimDeliveryRequest(5L, List.of(101L, 102L));

            DeliveryPerson person = new DeliveryPerson(5L, 1L, VehicleType.MOTO, Instant.now());
            OrderServicePort.OrderInfo orderInfo1 = new OrderServicePort.OrderInfo(101L, 10L, "Pickup1", "Delivery1");
            OrderServicePort.OrderInfo orderInfo2 = new OrderServicePort.OrderInfo(102L, 20L, "Pickup2", "Delivery2");

            when(deliveryPersonRepository.findById(5L))
                    .thenReturn(java.util.Optional.of(person));
            when(orderServicePort.getOrdersByIds(List.of(101L, 102L)))
                    .thenReturn(List.of(orderInfo1, orderInfo2));
            when(orderServicePort.areOrdersFromSameRestaurant(List.of(101L, 102L)))
                    .thenReturn(false);

            assertThatThrownBy(() -> useCase.execute(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("same restaurant");
        }
    }
}
