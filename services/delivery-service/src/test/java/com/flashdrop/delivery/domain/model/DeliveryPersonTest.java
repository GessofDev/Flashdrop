package com.flashdrop.delivery.domain.model;

import com.flashdrop.delivery.domain.valueobjects.VehicleType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DeliveryPerson")
class DeliveryPersonTest {

    @Nested
    @DisplayName("field mapping")
    class FieldMapping {

        @Test
        @DisplayName("TC1: all fields are correctly stored and retrieved")
        void allFieldsStoredAndRetrieved() {
            Instant createdAt = Instant.parse("2024-07-29T10:00:00Z");
            DeliveryPerson person = new DeliveryPerson(
                    7L, "U1", VehicleType.MOTO, createdAt
            );

            assertThat(person.getId()).isEqualTo(7L);
            assertThat(person.getUserId()).isEqualTo("U1");
            assertThat(person.getVehicle()).isEqualTo(VehicleType.MOTO);
            assertThat(person.getCreatedAt()).isEqualTo(createdAt);
        }

        @Test
        @DisplayName("TC2: vehicle can be any supported type")
        void supportedVehicleTypes() {
            Instant now = Instant.now();
            assertThat(new DeliveryPerson(1L, "U1", VehicleType.MOTO, now).getVehicle())
                    .isEqualTo(VehicleType.MOTO);
            assertThat(new DeliveryPerson(2L, "U2", VehicleType.AUTO, now).getVehicle())
                    .isEqualTo(VehicleType.AUTO);
            assertThat(new DeliveryPerson(3L, "U3", VehicleType.BICICLETA, now).getVehicle())
                    .isEqualTo(VehicleType.BICICLETA);
            assertThat(new DeliveryPerson(4L, "U4", VehicleType.PIE, now).getVehicle())
                    .isEqualTo(VehicleType.PIE);
        }
    }

    @Nested
    @DisplayName("userId uniqueness")
    class UserIdUniqueness {

        @Test
        @DisplayName("TC3: different userIds represent different persons")
        void differentUserIdsAreDifferentPersons() {
            Instant now = Instant.now();
            DeliveryPerson u1 = new DeliveryPerson(1L, "U1", VehicleType.MOTO, now);
            DeliveryPerson u2 = new DeliveryPerson(2L, "U2", VehicleType.AUTO, now);

            assertThat(u1.getUserId()).isNotEqualTo(u2.getUserId());
            assertThat(u1.getId()).isNotEqualTo(u2.getId());
        }
    }
}
