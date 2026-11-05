package cl.flashdrop.orders.infrastructure.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReleaseOrderRequest(
        @NotBlank(message = "El motivo es obligatorio")
        @Size(max = 500, message = "El motivo no puede exceder 500 caracteres")
        String reason
) {
}
