package cl.flashdrop.orders.infrastructure.adapter.outbound.http.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.json.JacksonTester;
import org.springframework.boot.test.json.JsonContent;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test dedicado para validar el contrato JSON del DTO interno de Orders C-8 (KAN-59).
 *
 * <p>Contrato según {@code MIGRATION_PLAN.md} sección 8.3:
 * {@code id, clientId, restaurantId, deliveryId, status, address}. {@code total} no
 * forma parte del contrato.</p>
 */
class InternalOrderDtoJsonTest {

    private JacksonTester<InternalOrderDto> json;

    @BeforeEach
    void setUp() {
        JacksonTester.initFields(this, new ObjectMapper());
    }

    @Test
    void shouldSerializeInternalOrderDtoCorrectly() throws Exception {
        InternalOrderDto dto = new InternalOrderDto(
                501L,
                10L,
                7L,
                9L,
                "En camino",
                "Av. Providencia 1200, Santiago"
        );

        JsonContent<InternalOrderDto> result = json.write(dto);

        assertThat(result).hasJsonPathNumberValue("@.id", 501);
        assertThat(result).hasJsonPathNumberValue("@.clientId", 10);
        assertThat(result).hasJsonPathNumberValue("@.restaurantId", 7);
        assertThat(result).hasJsonPathNumberValue("@.deliveryId", 9);
        assertThat(result).hasJsonPathStringValue("@.status", "En camino");
        assertThat(result).hasJsonPathStringValue("@.address", "Av. Providencia 1200, Santiago");
        assertThat(result).doesNotHaveJsonPath("@.total");
    }

    @Test
    void shouldSerializeNullDeliveryIdWhenOrderNotClaimedYet() throws Exception {
        InternalOrderDto dto = new InternalOrderDto(
                501L,
                10L,
                7L,
                null,
                "Preparando",
                "Av. Providencia 1200, Santiago"
        );

        JsonContent<InternalOrderDto> result = json.write(dto);

        assertThat(result).hasJsonPathValue("@.id");
        assertThat(result).extractingJsonPathValue("@.deliveryId").isNull();
    }

    @Test
    void shouldDeserializeInternalOrderDtoCorrectly() throws Exception {
        String jsonContent = "{\"id\":501,\"clientId\":10,\"restaurantId\":7,\"deliveryId\":9,"
                + "\"status\":\"En camino\",\"address\":\"Av. Providencia 1200, Santiago\"}";

        InternalOrderDto dto = json.parseObject(jsonContent);

        assertThat(dto.id()).isEqualTo(501L);
        assertThat(dto.clientId()).isEqualTo(10L);
        assertThat(dto.restaurantId()).isEqualTo(7L);
        assertThat(dto.deliveryId()).isEqualTo(9L);
        assertThat(dto.status()).isEqualTo("En camino");
        assertThat(dto.address()).isEqualTo("Av. Providencia 1200, Santiago");
    }
}
