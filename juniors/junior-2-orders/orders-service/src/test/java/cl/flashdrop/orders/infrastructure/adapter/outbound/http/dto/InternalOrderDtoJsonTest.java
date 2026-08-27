package cl.flashdrop.orders.infrastructure.adapter.outbound.http.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.json.JacksonTester;
import org.springframework.boot.test.json.JsonContent;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test dedicado para validar el contrato JSON del DTO interno de Orders C-8 (KAN-59).
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
                "En camino",
                10L,
                7L,
                BigDecimal.valueOf(25000)
        );

        JsonContent<InternalOrderDto> result = json.write(dto);

        assertThat(result).hasJsonPathNumberValue("@.id", 501);
        assertThat(result).hasJsonPathStringValue("@.status", "En camino");
        assertThat(result).hasJsonPathNumberValue("@.clientId", 10);
        assertThat(result).hasJsonPathNumberValue("@.restaurantId", 7);
        assertThat(result).hasJsonPathNumberValue("@.total", 25000);
    }

    @Test
    void shouldDeserializeInternalOrderDtoCorrectly() throws Exception {
        String jsonContent = "{\"id\":501,\"status\":\"En camino\",\"clientId\":10,\"restaurantId\":7,\"total\":25000}";

        InternalOrderDto dto = json.parseObject(jsonContent);

        assertThat(dto.id()).isEqualTo(501L);
        assertThat(dto.status()).isEqualTo("En camino");
        assertThat(dto.clientId()).isEqualTo(10L);
        assertThat(dto.restaurantId()).isEqualTo(7L);
        assertThat(dto.total()).isEqualTo(BigDecimal.valueOf(25000));
    }
}
