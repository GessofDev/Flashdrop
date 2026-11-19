package cl.flashdrop.orders.infrastructure.api;

import cl.flashdrop.orders.application.usecase.QuoteOrderUseCase;
import cl.flashdrop.orders.domain.model.DeliveryQuote;
import cl.flashdrop.orders.infrastructure.adapter.outbound.IdConverter;
import cl.flashdrop.orders.infrastructure.api.dto.request.QuoteOrderRequest;
import cl.flashdrop.orders.infrastructure.api.dto.response.ApiResponse;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * Cotizacion del pedido antes de pagar (F8): la app muestra subtotal, envio y total tal como el
 * servidor los cobrara al crear el pedido.
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderQuoteController {

    private final QuoteOrderUseCase quoteOrderUseCase;
    private final CurrentUserResolver currentUserResolver;

    /** {@code defaultFee = true}: se cobra el envio de respaldo y el desglose no aplica. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record QuoteResponse(BigDecimal subtotal, BigDecimal deliveryFee, BigDecimal total, boolean defaultFee,
                                String vehicle, Integer dimension, BigDecimal distanceKm,
                                BigDecimal dimensionCost, BigDecimal distanceCost) {
    }

    @PostMapping("/delivery-quote")
    public ApiResponse<QuoteResponse> quote(@Valid @RequestBody QuoteOrderRequest request) {
        currentUserResolver.requireCurrentUserId();
        QuoteOrderUseCase.OrderQuote result = quoteOrderUseCase.execute(
                request.getItems().stream()
                        .map(i -> new QuoteOrderUseCase.Item(
                                i.getProductId() != null ? IdConverter.toUuid(i.getProductId()) : null, i.getQuantity()))
                        .toList(),
                request.getLatitude(), request.getLongitude());
        DeliveryQuote d = result.delivery();
        return ApiResponse.success(new QuoteResponse(result.subtotal(), d.deliveryFee(), result.total(),
                d.defaultFee(), d.vehicle() == null ? null : d.vehicle().name(), d.dimension(), d.distanceKm(),
                d.dimensionCost(), d.distanceCost()));
    }
}
