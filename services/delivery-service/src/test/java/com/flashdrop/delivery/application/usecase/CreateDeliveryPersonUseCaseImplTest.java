package com.flashdrop.delivery.application.usecase;

import com.flashdrop.delivery.application.dto.CreateDeliveryPersonRequest;
import com.flashdrop.delivery.application.dto.DeliveryPersonResponse;
import com.flashdrop.delivery.application.port.outbound.DeliveryPersonRepository;
import com.flashdrop.delivery.domain.exception.DeliveryPersonAlreadyExistsException;
import com.flashdrop.delivery.domain.model.DeliveryPerson;
import com.flashdrop.delivery.domain.valueobjects.VehicleType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CreateDeliveryPersonUseCaseImpl}.
 *
 * <p>Verifies the three invariants the endpoint must hold:
 * <ol>
 *   <li>Idempotency by exception: if a profile already exists, the use
 *       case throws {@link DeliveryPersonAlreadyExistsException} and does
 *       NOT call {@code save()} (no DB write).</li>
 *   <li>Identity from the caller: the use case uses the {@code userId}
 *       passed in, never a value from the request body.</li>
 *   <li>Vehicle is optional: the request can carry null and the use case
 *       persists null; with a value, it persists that value.</li>
 * </ol>
 *
 * <p>The repo is mocked; this is a pure unit test.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CreateDeliveryPersonUseCaseImpl — courier self-signup")
class CreateDeliveryPersonUseCaseImplTest {

    @Mock
    private DeliveryPersonRepository deliveryPersonRepository;

    private CreateDeliveryPersonUseCaseImpl useCase;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        useCase = new CreateDeliveryPersonUseCaseImpl(deliveryPersonRepository);
    }

    @Nested
    @DisplayName("execute(Long userId, CreateDeliveryPersonRequest)")
    class Execute {

        @Test
        @DisplayName("TC1: no existing profile — creates with vehicle from request")
        void createsProfileWithVehicle() {
            long userId = 42L;
            CreateDeliveryPersonRequest request = new CreateDeliveryPersonRequest(VehicleType.MOTO);

            when(deliveryPersonRepository.existsByUserId("42")).thenReturn(false);
            when(deliveryPersonRepository.save(any(DeliveryPerson.class)))
                    .thenAnswer(inv -> {
                        DeliveryPerson input = inv.getArgument(0);
                        return new DeliveryPerson(7L, input.getUserId(), input.getVehicle(), Instant.now());
                    });

            DeliveryPersonResponse response = useCase.execute(userId, request);

            assertThat(response.id()).isEqualTo(7L);
            assertThat(response.userId()).isEqualTo("42");
            assertThat(response.vehicle()).isEqualTo("MOTO");

            // The IDOR-fix invariant: the use case saves a profile with the
            // userId from the parameter, not from the request body.
            ArgumentCaptor<DeliveryPerson> captor = ArgumentCaptor.forClass(DeliveryPerson.class);
            verify(deliveryPersonRepository).save(captor.capture());
            assertThat(captor.getValue().getUserId()).isEqualTo("42");
            assertThat(captor.getValue().getVehicle()).isEqualTo(VehicleType.MOTO);
        }

        @Test
        @DisplayName("TC2: no existing profile, vehicle is null — creates with vehicle=null")
        void createsProfileWithNullVehicle() {
            long userId = 7L;
            CreateDeliveryPersonRequest request = new CreateDeliveryPersonRequest(null);

            when(deliveryPersonRepository.existsByUserId("7")).thenReturn(false);
            when(deliveryPersonRepository.save(any(DeliveryPerson.class)))
                    .thenAnswer(inv -> {
                        DeliveryPerson input = inv.getArgument(0);
                        return new DeliveryPerson(11L, input.getUserId(), input.getVehicle(), Instant.now());
                    });

            DeliveryPersonResponse response = useCase.execute(userId, request);

            assertThat(response.vehicle()).isNull();
            assertThat(response.userId()).isEqualTo("7");

            ArgumentCaptor<DeliveryPerson> captor = ArgumentCaptor.forClass(DeliveryPerson.class);
            verify(deliveryPersonRepository).save(captor.capture());
            assertThat(captor.getValue().getVehicle()).isNull();
        }

        @Test
        @DisplayName("TC3: profile already exists — throws DeliveryPersonAlreadyExistsException, NO save()")
        void existingProfileThrows() {
            long userId = 42L;
            CreateDeliveryPersonRequest request = new CreateDeliveryPersonRequest(VehicleType.BICICLETA);

            when(deliveryPersonRepository.existsByUserId("42")).thenReturn(true);

            assertThatThrownBy(() -> useCase.execute(userId, request))
                    .isInstanceOf(DeliveryPersonAlreadyExistsException.class)
                    .hasMessageContaining("42");

            // Critical: when the profile already exists, we MUST NOT call
            // save(). A save() on a pre-existing user_id would either fail
            // with the UNIQUE constraint or, worse, overwrite the existing
            // row. The use case exists to prevent both.
            verify(deliveryPersonRepository, never()).save(any());
        }

        @Test
        @DisplayName("TC4: existing profile check uses the same userId form as findByUserId (Long.toString)")
        void existingProfileCheckUsesLongToString() {
            // The repo's findByUserId takes a String; the DB column is VARCHAR.
            // The use case MUST convert the Long userId to String consistently
            // with the rest of the service (Long.toString), or the existence
            // check would miss an existing row that was saved with the same
            // conversion.
            long userId = 42L;
            when(deliveryPersonRepository.existsByUserId("42")).thenReturn(false);
            when(deliveryPersonRepository.save(any()))
                    .thenAnswer(inv -> new DeliveryPerson(1L, "42", null, Instant.now()));

            useCase.execute(userId, new CreateDeliveryPersonRequest(null));

            verify(deliveryPersonRepository).existsByUserId("42");
            // Sanity: never called with the raw Long as a String — that would
            // produce "42" (toString) but the contract is Long.toString, not
            // String.valueOf. They happen to match for positive longs but
            // the contract should be explicit.
            verify(deliveryPersonRepository, never()).existsByUserId("42 ");
            verify(deliveryPersonRepository, never()).existsByUserId((String) null);
        }
    }
}
