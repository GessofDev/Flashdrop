package cl.flashdrop.orders.infrastructure.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CancelAfterPickupRequest(
        @NotBlank(message = "El estado final es obligatorio")
        @Pattern(regexp = "(?i)^(Cancelado|Rechazado)$", message = "El estado final debe ser Cancelado o Rechazado")
        String status,

        @NotBlank(message = "El motivo es obligatorio")
        @Size(max = 500, message = "El motivo no puede exceder 500 caracteres")
        String reason
) {
}
