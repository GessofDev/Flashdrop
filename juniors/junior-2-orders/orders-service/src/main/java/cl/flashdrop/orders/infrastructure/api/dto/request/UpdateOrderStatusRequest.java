package cl.flashdrop.orders.infrastructure.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO para actualizar el estado de un pedido.
 */
@Getter
@Setter
@NoArgsConstructor
public class UpdateOrderStatusRequest {

    @NotBlank(message = "El estado es obligatorio")
    private String status;
}
