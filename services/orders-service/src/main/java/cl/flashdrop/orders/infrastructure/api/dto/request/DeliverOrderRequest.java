package cl.flashdrop.orders.infrastructure.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cuerpo de {@code POST /api/orders/{id}/deliver}: el código de 6 dígitos que el cliente le
 * dicta al repartidor. En modo NORMAL la app del repartidor envía {@code 000000}.
 */
@Getter
@Setter
@NoArgsConstructor
public class DeliverOrderRequest {

    @NotBlank(message = "El codigo es obligatorio")
    @Pattern(regexp = "\\d{6}", message = "El codigo debe tener 6 digitos")
    private String code;
}
