package cl.flashdrop.orders.infrastructure.api;

import cl.flashdrop.orders.application.usecase.GetOrderPaymentUseCase;
import cl.flashdrop.orders.application.usecase.RetryPaymentUseCase;
import cl.flashdrop.orders.infrastructure.api.dto.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Pago de un pedido (F8), para el cliente dueño: ver como va el pago y abrir un nuevo intento
 * cuando el anterior fue rechazado.
 */
@RestController
@RequestMapping("/api/orders/{id}/payments")
@RequiredArgsConstructor
public class OrderPaymentController {

    private final GetOrderPaymentUseCase getOrderPaymentUseCase;
    private final RetryPaymentUseCase retryPaymentUseCase;
    private final CurrentUserResolver currentUserResolver;

    /** Estado del pedido y del ultimo intento de pago. La app lo consulta mientras espera la confirmacion. */
    @GetMapping
    public ApiResponse<GetOrderPaymentUseCase.PaymentView> status(@PathVariable("id") String rawOrderId) {
        UUID orderId = OrderIdResolver.resolve(rawOrderId);
        return ApiResponse.success(getOrderPaymentUseCase.execute(orderId, currentUserResolver.requireCurrentUserId()));
    }

    /** Abre un nuevo intento de pago para el mismo pedido (o devuelve el que sigue abierto). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RetryPaymentUseCase.OpenPayment> retry(@PathVariable("id") String rawOrderId) {
        UUID orderId = OrderIdResolver.resolve(rawOrderId);
        return ApiResponse.success("Pago abierto",
                retryPaymentUseCase.execute(orderId, currentUserResolver.requireCurrentUserId()));
    }
}
