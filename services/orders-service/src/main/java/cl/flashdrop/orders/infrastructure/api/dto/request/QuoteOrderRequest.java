package cl.flashdrop.orders.infrastructure.api.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/** Cotizacion de un pedido antes de pagar: productos y, si se conoce, el destino. */
@Getter
@Setter
@NoArgsConstructor
public class QuoteOrderRequest {

    @NotEmpty(message = "El pedido debe contener al menos un producto")
    @Valid
    private List<CreateOrderRequest.ItemRequest> items;

    @JsonAlias("delivery_latitude")
    @DecimalMin(value = "-90", message = "La latitud debe estar entre -90 y 90")
    @DecimalMax(value = "90", message = "La latitud debe estar entre -90 y 90")
    private BigDecimal latitude;

    @JsonAlias("delivery_longitude")
    @DecimalMin(value = "-180", message = "La longitud debe estar entre -180 y 180")
    @DecimalMax(value = "180", message = "La longitud debe estar entre -180 y 180")
    private BigDecimal longitude;
}
